package com.nexusai.ai;

import com.nexusai.ai.AiRepositories.ConversationRepository;
import com.nexusai.ai.AiRepositories.MessageRepository;
import com.nexusai.ai.AiRepositories.ToolExecutionRepository;
import com.nexusai.ai.llm.LlmClient;
import com.nexusai.ai.llm.LlmException;
import com.nexusai.ai.llm.LlmModels.LlmMessage;
import com.nexusai.ai.llm.LlmModels.LlmReply;
import com.nexusai.ai.llm.LlmModels.LlmRequest;
import com.nexusai.ai.llm.LlmModels.ToolCall;
import com.nexusai.ai.llm.LlmModels.ToolResult;
import com.nexusai.ai.llm.LlmModels.ToolSpec;
import com.nexusai.ai.tools.ToolRegistry;
import com.nexusai.ai.tools.ToolSupport.Args;
import com.nexusai.ai.tools.ToolSupport.Tool;
import com.nexusai.ai.tools.ToolSupport.ToolContext;
import com.nexusai.ai.tools.ToolSupport.ToolException;
import com.nexusai.audit.AuditService;
import com.nexusai.common.PeriodResolver;
import com.nexusai.common.RateLimiter;
import com.nexusai.common.exception.ApiException;
import com.nexusai.config.AppProperties;
import com.nexusai.security.OrgContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Orquestrador do agente: historico autorizado -> LLM -> chamadas de ferramenta validadas no servidor ->
 * resposta fundamentada. Limites: maximo de chamadas de ferramenta, deteccao de chamadas repetidas,
 * timeout por chamada ao provedor e historico truncado. Nao mantem transacao de banco aberta durante
 * chamadas ao provedor.
 */
@Service
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);
    private static final int MAX_TOOL_RESULT_CHARS = 14_000;
    private static final int MAX_DUPLICATE_CALLS = 2;

    public record ToolTrace(String tool, String status, long durationMs, String arguments) {}

    public record AssistantReply(UUID messageId, String content, List<ToolTrace> toolCalls) {}

    private final LlmClient llm;
    private final ToolRegistry registry;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ToolExecutionRepository executions;
    private final AuditService audit;
    private final PeriodResolver periods;
    private final AppProperties props;
    private final JsonMapper json;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final RateLimiter limiter;

    public AiAgentService(LlmClient llm, ToolRegistry registry, ConversationRepository conversations,
            MessageRepository messages, ToolExecutionRepository executions, AuditService audit,
            PeriodResolver periods, AppProperties props, JsonMapper json, MeterRegistry metrics, Clock clock) {
        this.llm = llm;
        this.registry = registry;
        this.conversations = conversations;
        this.messages = messages;
        this.executions = executions;
        this.audit = audit;
        this.periods = periods;
        this.props = props;
        this.json = json;
        this.metrics = metrics;
        this.clock = clock;
        this.limiter = new RateLimiter(props.ai().rateLimitPerMinute(), 60_000L, clock);
    }

    public boolean isConfigured() {
        return llm.isConfigured();
    }

    public AssistantReply respond(OrgContext ctx, UUID conversationId, String userText) {
        if (!llm.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                    "O assistente de IA nao esta configurado. Defina AI_PROVIDER, AI_MODEL e AI_API_KEY no backend.");
        }
        if (!limiter.tryAcquire(ctx.userId().toString())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Limite de mensagens por minuto atingido. Aguarde um instante.");
        }
        Conversation conv = conversations
                .findByIdAndOrganizationIdAndUserId(conversationId, ctx.organizationId(), ctx.userId())
                .orElseThrow(() -> ApiException.notFound("Conversa"));
        Instant now = Instant.now(clock);
        messages.save(new ChatMessage(conversationId, ChatMessage.Role.USER, userText, now));
        if ("Nova conversa".equals(conv.getTitle())) {
            conv.rename(userText.length() > 60 ? userText.substring(0, 60) + "..." : userText);
            conversations.save(conv);
        }

        List<LlmMessage> convo = new ArrayList<>(loadHistory(conversationId));
        List<ToolSpec> specs = registry.availableFor(ctx.role()).stream()
                .map(t -> new ToolSpec(t.name(), t.description(), t.schemaJson())).toList();
        List<AiToolExecution> executed = new ArrayList<>();
        List<ToolTrace> traces = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        int maxCalls = props.ai().maxToolCalls();
        int calls = 0;
        int duplicates = 0;
        String finalText = null;
        String system = SystemPrompt.build(ctx, periods.today(), props.timezone());
        Timer.Sample sample = Timer.start(metrics);

        try {
            for (int iteration = 0; iteration <= maxCalls + 1; iteration++) {
                boolean toolsAllowed = calls < maxCalls && duplicates <= MAX_DUPLICATE_CALLS;
                LlmReply reply = callLlm(new LlmRequest(system, convo, toolsAllowed ? specs : List.of()));
                if (!reply.hasToolCalls() || !toolsAllowed) {
                    finalText = reply.text();
                    break;
                }
                convo.add(LlmMessage.assistant(reply.text(), reply.toolCalls()));
                List<ToolResult> results = new ArrayList<>();
                for (ToolCall call : reply.toolCalls()) {
                    String key = call.name() + ":" + normalize(call.argumentsJson());
                    if (calls >= maxCalls) {
                        results.add(new ToolResult(call.id(), call.name(),
                                error("Orcamento de chamadas de ferramenta esgotado; responda com os dados ja obtidos."), true));
                    } else if (seen.merge(key, 1, Integer::sum) > 1) {
                        duplicates++;
                        results.add(new ToolResult(call.id(), call.name(),
                                error("Chamada repetida com os mesmos argumentos; reutilize o resultado anterior."), true));
                    } else {
                        calls++;
                        results.add(runTool(ctx, conversationId, call, executed, traces));
                    }
                }
                convo.add(LlmMessage.toolResults(results));
            }
            if (finalText == null || finalText.isBlank()) {
                finalText = "Nao consegui concluir a analise com os dados disponiveis. Reformule a pergunta ou tente novamente.";
            }
        } catch (RuntimeException e) {
            executions.saveAll(executed); // preserva rastro das ferramentas executadas ate a falha
            metrics.counter("nexus.ai.requests", "outcome", "error").increment();
            throw e;
        } finally {
            sample.stop(metrics.timer("nexus.ai.request.duration"));
        }

        ChatMessage assistant = messages.save(new ChatMessage(conversationId, ChatMessage.Role.ASSISTANT, finalText,
                Instant.now(clock)));
        executed.forEach(e -> e.attachToMessage(assistant.getId()));
        executions.saveAll(executed);
        audit.record(ctx.organizationId(), ctx.userId(), "AI_INTERACTION", "Conversation", conversationId,
                "ferramentas=" + executed.size());
        metrics.counter("nexus.ai.requests", "outcome", "ok").increment();
        return new AssistantReply(assistant.getId(), finalText, traces);
    }

    private LlmReply callLlm(LlmRequest request) {
        try {
            return llm.chat(request);
        } catch (LlmException e) {
            log.warn("Falha no provedor de IA: {}", e.getMessage());
            metrics.counter("nexus.ai.provider.errors", "timeout", String.valueOf(e.isTimeout())).increment();
            if (e.isTimeout()) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT",
                        "O provedor de IA demorou demais para responder. Tente novamente.");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR",
                    "O provedor de IA esta indisponivel no momento. As demais funcionalidades continuam disponiveis.");
        }
    }

    private ToolResult runTool(OrgContext ctx, UUID conversationId, ToolCall call, List<AiToolExecution> executed,
            List<ToolTrace> traces) {
        long start = System.nanoTime();
        AiToolExecution.Status status;
        String content;
        Tool tool = registry.find(call.name()).filter(t -> ctx.role().atLeast(t.minRole())).orElse(null);
        if (tool == null) {
            status = AiToolExecution.Status.DENIED;
            content = error("Ferramenta desconhecida ou nao permitida para este perfil.");
        } else {
            try {
                JsonNode node = json.readTree(call.argumentsJson() == null || call.argumentsJson().isBlank()
                        ? "{}" : call.argumentsJson());
                if (!node.isObject() || call.argumentsJson() != null && call.argumentsJson().length() > 8000) {
                    throw new ToolException("Argumentos devem ser um objeto JSON de ate 8000 caracteres");
                }
                Object out = tool.handler().handle(new ToolContext(ctx, conversationId), new Args(node));
                content = json.writeValueAsString(out);
                if (content.length() > MAX_TOOL_RESULT_CHARS) {
                    content = content.substring(0, MAX_TOOL_RESULT_CHARS) + "...[resultado truncado]";
                }
                status = AiToolExecution.Status.SUCCESS;
            } catch (ToolException | ApiException e) {
                status = AiToolExecution.Status.ERROR;
                content = error(e.getMessage());
            } catch (tools.jackson.core.JacksonException e) {
                status = AiToolExecution.Status.ERROR;
                content = error("Argumentos nao sao um JSON valido");
            } catch (RuntimeException e) {
                log.error("Erro inesperado na ferramenta {}", call.name(), e);
                status = AiToolExecution.Status.ERROR;
                content = error("Erro interno ao executar a ferramenta");
            }
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        String args = call.argumentsJson() == null ? "{}" : call.argumentsJson();
        executed.add(new AiToolExecution(ctx.organizationId(), ctx.userId(), conversationId, call.name(), args,
                content.length() > 500 ? content.substring(0, 500) : content, status, ms, Instant.now(clock)));
        traces.add(new ToolTrace(call.name(), status.name(), ms, args.length() > 300 ? args.substring(0, 300) : args));
        metrics.counter("nexus.ai.tool.calls", "tool", call.name(), "status", status.name()).increment();
        return new ToolResult(call.id(), call.name(), content, status != AiToolExecution.Status.SUCCESS);
    }

    private List<LlmMessage> loadHistory(UUID conversationId) {
        List<ChatMessage> recent = new ArrayList<>(messages.findByConversationIdOrderByCreatedAtDesc(conversationId,
                PageRequest.of(0, Math.max(1, props.ai().maxConversationHistory()))));
        Collections.reverse(recent);
        List<LlmMessage> out = new ArrayList<>();
        for (ChatMessage m : recent) {
            // Historico e contexto conversacional, nao fonte de fatos: dados atuais vem das ferramentas.
            out.add(m.getRole() == ChatMessage.Role.USER ? LlmMessage.user(m.getContent())
                    : LlmMessage.assistant(m.getContent(), List.of()));
        }
        // Provedores exigem que a primeira mensagem seja do usuario.
        while (!out.isEmpty() && out.get(0).role() != com.nexusai.ai.llm.LlmModels.Role.USER) {
            out.remove(0);
        }
        return out;
    }

    private String normalize(String argsJson) {
        try {
            return json.writeValueAsString(json.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson));
        } catch (RuntimeException e) {
            return String.valueOf(argsJson);
        }
    }

    private String error(String message) {
        return json.writeValueAsString(Map.of("error", message));
    }
}
