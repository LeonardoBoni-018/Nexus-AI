package com.nexusai.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexusai.ai.llm.LlmModels.*;
import com.nexusai.config.AppProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Testes de serializacao/parsing dos adaptadores (sem rede). Nao validam o provedor real. */
class ProviderAdaptersTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final AppProperties.Ai cfg = new AppProperties.Ai("anthropic", "modelo-x", "chave-secreta", "",
            8, Duration.ofSeconds(5), 512, 20, 20);
    private final LlmRequest request = new LlmRequest("sistema", List.of(
            LlmMessage.user("oi"),
            LlmMessage.assistant("vou consultar", List.of(new ToolCall("t1", "getSalesSummary", "{\"from\":\"2026-01-01\"}"))),
            LlmMessage.toolResults(List.of(new ToolResult("t1", "getSalesSummary", "{\"ok\":true}", false)))),
            List.of(new ToolSpec("getSalesSummary", "desc", "{\"type\":\"object\",\"properties\":{}}")));

    @Test
    void anthropicBodyUsesToolUseAndToolResultBlocksAndNeverEmbedsTheKey() {
        String body = new AnthropicClient(cfg, json).buildBody(request);
        JsonNode root = json.readTree(body);
        assertEquals("modelo-x", root.get("model").asString());
        assertEquals("sistema", root.get("system").asString());
        assertEquals("tool_use", root.get("messages").get(1).get("content").get(1).get("type").asString());
        assertEquals("tool_result", root.get("messages").get(2).get("content").get(0).get("type").asString());
        assertEquals("object", root.get("tools").get(0).get("input_schema").get("type").asString());
        assertFalse(body.contains("chave-secreta"));
    }

    @Test
    void anthropicParsesTextAndToolUse() {
        LlmReply r = new AnthropicClient(cfg, json).parse("""
                {"content":[{"type":"text","text":"Analisando"},
                {"type":"tool_use","id":"abc","name":"getLowStockProducts","input":{"limit":3}}]}""");
        assertEquals("Analisando", r.text());
        assertTrue(r.hasToolCalls());
        assertEquals("getLowStockProducts", r.toolCalls().get(0).name());
        assertEquals("{\"limit\":3}", r.toolCalls().get(0).argumentsJson());
    }

    @Test
    void openAiBodyAndParsing() {
        OpenAiCompatibleClient c = new OpenAiCompatibleClient(cfg, json);
        JsonNode root = json.readTree(c.buildBody(request, false));
        assertEquals("system", root.get("messages").get(0).get("role").asString());
        assertEquals("tool", root.get("messages").get(3).get("role").asString());
        assertTrue(root.has("max_tokens"));
        assertTrue(json.readTree(c.buildBody(request, true)).has("max_completion_tokens"));
        LlmReply r = c.parse("""
                {"choices":[{"message":{"content":null,"tool_calls":[
                {"id":"x","type":"function","function":{"name":"getActionStatus","arguments":"{\\"actionId\\":\\"1\\"}"}}]}}]}""");
        assertEquals("getActionStatus", r.toolCalls().get(0).name());
        assertEquals("{\"actionId\":\"1\"}", r.toolCalls().get(0).argumentsJson());
    }

    @Test
    void malformedProviderResponsesRaiseLlmException() {
        assertThrows(LlmException.class, () -> new AnthropicClient(cfg, json).parse("nao-json"));
        assertThrows(LlmException.class, () -> new AnthropicClient(cfg, json).parse("{\"foo\":1}"));
        assertThrows(LlmException.class, () -> new OpenAiCompatibleClient(cfg, json).parse("{\"choices\":[]}"));
    }
}
