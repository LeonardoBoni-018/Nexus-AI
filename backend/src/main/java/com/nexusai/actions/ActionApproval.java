package com.nexusai.actions;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "action_approvals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionApproval {

    public enum Decision { APPROVED, REJECTED }

    @Id
    private UUID id;
    @Column(name = "action_id", nullable = false)
    private UUID actionId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ActionApproval(UUID actionId, UUID userId, Decision decision, String reason, Instant now) {
        this.id = UUID.randomUUID();
        this.actionId = actionId;
        this.userId = userId;
        this.decision = decision;
        this.reason = reason;
        this.createdAt = now;
    }
}
