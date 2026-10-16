package com.nexusai.ai;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private String title;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Conversation(UUID organizationId, UUID userId, String title, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.userId = userId;
        this.title = title.length() > 190 ? title.substring(0, 190) : title;
        this.createdAt = now;
    }

    public void rename(String title) {
        this.title = title.length() > 190 ? title.substring(0, 190) : title;
    }
}
