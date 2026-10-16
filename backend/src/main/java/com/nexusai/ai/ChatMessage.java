package com.nexusai.ai;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    public enum Role { USER, ASSISTANT }

    @Id
    private UUID id;
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    @Column(nullable = false)
    private String content;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ChatMessage(UUID conversationId, Role role, String content, Instant now) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.role = role;
        this.content = content.length() > 19000 ? content.substring(0, 19000) : content;
        this.createdAt = now;
    }
}
