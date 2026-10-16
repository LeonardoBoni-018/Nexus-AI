package com.nexusai.ai;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_tool_executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiToolExecution {
    public enum Status { SUCCESS, ERROR, DENIED }

    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
    @Column(name = "message_id")
    private UUID messageId;
    @Column(name = "tool_name", nullable = false)
    private String toolName;
    private String arguments;
    @Column(name = "result_summary")
    private String resultSummary;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AiToolExecution(UUID organizationId, UUID userId, UUID conversationId, String toolName, String arguments,
            String resultSummary, Status status, long durationMs, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.userId = userId;
        this.conversationId = conversationId;
        this.toolName = toolName;
        this.arguments = cut(arguments, 3900);
        this.resultSummary = cut(resultSummary, 3900);
        this.status = status;
        this.durationMs = durationMs;
        this.createdAt = now;
    }

    public void attachToMessage(UUID messageId) {
        this.messageId = messageId;
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
