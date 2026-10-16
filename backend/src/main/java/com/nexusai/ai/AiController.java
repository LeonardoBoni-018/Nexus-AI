package com.nexusai.ai;

import com.nexusai.ai.AiAgentService.AssistantReply;
import com.nexusai.ai.AiAgentService.ToolTrace;
import com.nexusai.ai.AiRepositories.ConversationRepository;
import com.nexusai.ai.AiRepositories.MessageRepository;
import com.nexusai.ai.AiRepositories.ToolExecutionRepository;
import com.nexusai.common.exception.ApiException;
import com.nexusai.config.AppProperties;
import com.nexusai.security.OrgContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AiController {

    public record CreateConversationRequest(@Size(max = 190) String title) {}

    public record ConversationDto(UUID id, String title, Instant createdAt) {}

    public record SendMessageRequest(@NotBlank @Size(max = 4000) String content) {}

    public record MessageDto(UUID id, String role, String content, Instant createdAt, List<ToolTrace> toolCalls) {}

    public record StatusDto(boolean configured, String provider, String model, String message) {}

    private final AiAgentService agent;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ToolExecutionRepository executions;
    private final AppProperties props;
    private final Clock clock;

    public AiController(AiAgentService agent, ConversationRepository conversations, MessageRepository messages,
            ToolExecutionRepository executions, AppProperties props, Clock clock) {
        this.agent = agent;
        this.conversations = conversations;
        this.messages = messages;
        this.executions = executions;
        this.props = props;
        this.clock = clock;
    }

    /** Nunca expõe a chave; informa apenas se a integracao esta pronta. */
    @GetMapping("/status")
    public StatusDto status(OrgContext ctx) {
        boolean ok = agent.isConfigured();
        return new StatusDto(ok, ok ? props.ai().provider() : null, ok ? props.ai().model() : null,
                ok ? "Assistente disponivel"
                        : "Integracao de IA nao configurada: defina AI_PROVIDER, AI_MODEL e AI_API_KEY no backend.");
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDto create(OrgContext ctx, @Valid @RequestBody(required = false) CreateConversationRequest body) {
        String title = body == null || body.title() == null || body.title().isBlank() ? "Nova conversa" : body.title().trim();
        Conversation c = conversations.save(new Conversation(ctx.organizationId(), ctx.userId(), title, Instant.now(clock)));
        return new ConversationDto(c.getId(), c.getTitle(), c.getCreatedAt());
    }

    @GetMapping("/conversations")
    public List<ConversationDto> list(OrgContext ctx) {
        return conversations.findByOrganizationIdAndUserIdOrderByCreatedAtDesc(ctx.organizationId(), ctx.userId(),
                PageRequest.of(0, 50)).stream().map(c -> new ConversationDto(c.getId(), c.getTitle(), c.getCreatedAt())).toList();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageDto> history(OrgContext ctx, @PathVariable UUID id) {
        conversations.findByIdAndOrganizationIdAndUserId(id, ctx.organizationId(), ctx.userId())
                .orElseThrow(() -> ApiException.notFound("Conversa"));
        Map<UUID, List<ToolTrace>> traces = executions.findByConversationIdOrderByCreatedAtAsc(id).stream()
                .filter(e -> e.getMessageId() != null)
                .collect(Collectors.groupingBy(AiToolExecution::getMessageId, Collectors.mapping(
                        e -> new ToolTrace(e.getToolName(), e.getStatus().name(), e.getDurationMs(), e.getArguments()),
                        Collectors.toList())));
        List<MessageDto> out = new ArrayList<>();
        for (ChatMessage m : messages.findByConversationIdOrderByCreatedAtAsc(id)) {
            out.add(new MessageDto(m.getId(), m.getRole().name(), m.getContent(), m.getCreatedAt(),
                    traces.getOrDefault(m.getId(), List.of())));
        }
        return out;
    }

    @PostMapping("/conversations/{id}/messages")
    public AssistantReply send(OrgContext ctx, @PathVariable UUID id, @Valid @RequestBody SendMessageRequest body) {
        return agent.respond(ctx, id, body.content().trim());
    }
}
