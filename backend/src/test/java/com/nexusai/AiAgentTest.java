package com.nexusai;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nexusai.ai.llm.LlmClient;
import com.nexusai.ai.llm.LlmException;
import com.nexusai.ai.llm.LlmModels.LlmReply;
import com.nexusai.ai.llm.LlmModels.LlmRequest;
import com.nexusai.ai.llm.LlmModels.ToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Orquestracao do agente com provedor simulado (nenhuma chamada paga de IA e feita). */
@Import(AiAgentTest.Config.class)
class AiAgentTest extends IntegrationTestBase {

    static class ScriptedLlm implements LlmClient {
        Function<LlmRequest, LlmReply> script = r -> new LlmReply("ok", List.of());
        final List<LlmRequest> requests = new ArrayList<>();

        public boolean isConfigured() { return true; }
        public String providerName() { return "scripted"; }
        public LlmReply chat(LlmRequest r) {
            requests.add(r);
            return script.apply(r);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ScriptedLlm scriptedLlm() {
            return new ScriptedLlm();
        }
    }

    @Autowired
    ScriptedLlm llm;

    @BeforeEach
    void reset() {
        llm.requests.clear();
        llm.script = r -> new LlmReply("ok", List.of());
    }

    private String newConversation(MockHttpSession s) throws Exception {
        String body = mvc.perform(post("/api/v1/assistant/conversations").session(s).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions send(MockHttpSession s, String conv, String text)
            throws Exception {
        return mvc.perform(post("/api/v1/assistant/conversations/" + conv + "/messages").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + text + "\"}"));
    }

    @Test
    void agentCallsToolsThenAnswersAndCreatesOnlyADraft() throws Exception {
        UUID store = storeId(orgId("mercado-aurora-demo"), "Loja Centro");
        llm.script = r -> {
            long toolRounds = r.messages().stream().filter(m -> m.role() == com.nexusai.ai.llm.LlmModels.Role.TOOL).count();
            if (toolRounds == 0) {
                return new LlmReply("", List.of(
                        new ToolCall("c1", "getSalesSummary", "{\"from\":\"2026-01-01\",\"to\":\"2026-12-31\"}"),
                        new ToolCall("c2", "getLowStockProducts", "{\"limit\":5}")));
            }
            if (toolRounds == 1) {
                return new LlmReply("", List.of(new ToolCall("c3", "createReplenishmentDraft",
                        "{\"storeId\":\"" + store + "\",\"justification\":\"Baixo estoque identificado\"}")));
            }
            return new LlmReply("Criei o rascunho para aprovacao humana.", List.of());
        };
        MockHttpSession s = login("analista@aurora.demo");
        String conv = newConversation(s);
        send(s, conv, "Analise o estoque e prepare reposicao").andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Criei o rascunho para aprovacao humana.")))
                .andExpect(jsonPath("$.toolCalls", hasSize(3)))
                .andExpect(jsonPath("$.toolCalls[2].tool", is("createReplenishmentDraft")))
                .andExpect(jsonPath("$.toolCalls[2].status", is("SUCCESS")));

        int drafts = jdbc.sql("SELECT COUNT(*) FROM proposed_actions WHERE status = 'DRAFT' AND created_via_ai = TRUE AND conversation_id = :c")
                .param("c", UUID.fromString(conv)).query(Integer.class).single();
        assertEquals(1, drafts);
        int notDraft = jdbc.sql("SELECT COUNT(*) FROM proposed_actions WHERE created_via_ai = TRUE AND status <> 'DRAFT'")
                .query(Integer.class).single();
        assertEquals(0, notDraft, "a IA nunca pode avancar alem de DRAFT");
        int persisted = jdbc.sql("SELECT COUNT(*) FROM ai_tool_executions WHERE conversation_id = :c AND message_id IS NOT NULL")
                .param("c", UUID.fromString(conv)).query(Integer.class).single();
        assertEquals(3, persisted);

        mvc.perform(get("/api/v1/assistant/conversations/" + conv + "/messages").session(s))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].toolCalls", hasSize(3)));
    }

    @Test
    void modelCannotReachAnotherOrganizationsData() throws Exception {
        UUID foreignStore = storeId(orgId("casa-e-cia-demo"), "Casa Matriz");
        llm.script = r -> {
            boolean hadTool = r.messages().stream().anyMatch(m -> m.role() == com.nexusai.ai.llm.LlmModels.Role.TOOL);
            return hadTool ? new LlmReply("Nao foi possivel.", List.of())
                    : new LlmReply("", List.of(new ToolCall("x1", "createReplenishmentDraft",
                            "{\"storeId\":\"" + foreignStore + "\",\"justification\":\"x\"}")));
        };
        MockHttpSession s = login("analista@aurora.demo");
        int before = jdbc.sql("SELECT COUNT(*) FROM proposed_actions").query(Integer.class).single();
        send(s, newConversation(s), "Reponha a loja do concorrente").andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCalls[0].status", is("ERROR")));
        int after = jdbc.sql("SELECT COUNT(*) FROM proposed_actions").query(Integer.class).single();
        assertEquals(before, after);
    }

    @Test
    void viewerIsNotOfferedOrAllowedWriteTools() throws Exception {
        llm.script = r -> {
            boolean hadTool = r.messages().stream().anyMatch(m -> m.role() == com.nexusai.ai.llm.LlmModels.Role.TOOL);
            if (hadTool) {
                return new LlmReply("Sem permissao.", List.of());
            }
            assertTrue(r.tools().stream().noneMatch(t -> t.name().equals("createReplenishmentDraft")),
                    "VIEWER nao deve receber a ferramenta de escrita");
            return new LlmReply("", List.of(new ToolCall("v1", "createReplenishmentDraft",
                    "{\"storeId\":\"" + UUID.randomUUID() + "\",\"justification\":\"x\"}")));
        };
        MockHttpSession s = login("leitor@aurora.demo");
        send(s, newConversation(s), "Crie uma proposta").andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCalls[0].status", is("DENIED")));
    }

    @Test
    void invalidArgumentsAreReportedToTheModelWithoutExecuting() throws Exception {
        llm.script = r -> {
            boolean hadTool = r.messages().stream().anyMatch(m -> m.role() == com.nexusai.ai.llm.LlmModels.Role.TOOL);
            return hadTool ? new LlmReply("Corrigi.", List.of())
                    : new LlmReply("", List.of(new ToolCall("b1", "getSalesSummary", "{\"from\":\"ontem\",\"to\":\"hoje\"}"),
                            new ToolCall("b2", "unknownTool", "{}"), new ToolCall("b3", "getSalesSummary", "nao e json")));
        };
        MockHttpSession s = login("analista@aurora.demo");
        send(s, newConversation(s), "teste").andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCalls[0].status", is("ERROR")))
                .andExpect(jsonPath("$.toolCalls[1].status", is("DENIED")))
                .andExpect(jsonPath("$.toolCalls[2].status", is("ERROR")));
    }

    @Test
    void repeatedIdenticalCallsAreBlockedAndLoopTerminates() throws Exception {
        llm.script = r -> r.tools().isEmpty() ? new LlmReply("Resumo final.", List.of())
                : new LlmReply("", List.of(new ToolCall("r" + llm.requests.size(), "getLowStockProducts", "{\"limit\":3}")));
        MockHttpSession s = login("analista@aurora.demo");
        send(s, newConversation(s), "loop").andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Resumo final.")))
                .andExpect(jsonPath("$.toolCalls", hasSize(1)));
        assertTrue(llm.requests.size() <= 10, "o ciclo deve ser limitado, requests=" + llm.requests.size());
    }

    @Test
    void toolCallBudgetIsEnforced() throws Exception {
        llm.script = r -> r.tools().isEmpty() ? new LlmReply("Encerrado.", List.of())
                : new LlmReply("", List.of(new ToolCall("t" + llm.requests.size(), "getLowStockProducts",
                        "{\"limit\":" + (1 + llm.requests.size() % 40) + "}")));
        MockHttpSession s = login("analista@aurora.demo");
        send(s, newConversation(s), "muitas ferramentas").andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Encerrado.")));
        long executed = jdbc.sql("SELECT COUNT(*) FROM ai_tool_executions").query(Long.class).single();
        assertTrue(executed <= 8 * 6, "orcamento de chamadas deve limitar execucoes");
    }

    @Test
    void providerFailureReturns502AndDoesNotBreakOtherFeatures() throws Exception {
        llm.script = r -> { throw new LlmException("falha simulada"); };
        MockHttpSession s = login("analista@aurora.demo");
        send(s, newConversation(s), "ola").andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code", is("AI_PROVIDER_ERROR")));
        llm.script = r -> { throw new LlmException("timeout", null, true); };
        send(s, newConversation(s), "ola").andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code", is("AI_TIMEOUT")));
        mvc.perform(get("/api/v1/dashboard/summary").session(s)).andExpect(status().isOk());
    }

    @Test
    void conversationsAreNotAccessibleToOtherUsers() throws Exception {
        MockHttpSession a = login("analista@aurora.demo");
        String conv = newConversation(a);
        MockHttpSession other = login("gerente@aurora.demo");
        mvc.perform(get("/api/v1/assistant/conversations/" + conv + "/messages").session(other))
                .andExpect(status().isNotFound());
        send(other, conv, "intruso").andExpect(status().isNotFound());
    }

    @Test
    void overlongMessagesAreRejected() throws Exception {
        MockHttpSession s = login("analista@aurora.demo");
        send(s, newConversation(s), "x".repeat(4001)).andExpect(status().isBadRequest());
    }
}
