package com.nexusai.actions;

import com.nexusai.common.exception.ApiException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Proposta de acao operacional com maquina de estados explicita. */
@Entity
@Table(name = "proposed_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposedAction {

    public enum Status { DRAFT, PENDING_APPROVAL, APPROVED, EXECUTED, REJECTED, CANCELLED, FAILED }

    public enum Type { REPLENISHMENT }

    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.DRAFT, EnumSet.of(Status.PENDING_APPROVAL, Status.CANCELLED),
            Status.PENDING_APPROVAL, EnumSet.of(Status.APPROVED, Status.REJECTED, Status.CANCELLED),
            Status.APPROVED, EnumSet.of(Status.EXECUTED, Status.FAILED, Status.CANCELLED),
            Status.EXECUTED, EnumSet.noneOf(Status.class),
            Status.REJECTED, EnumSet.noneOf(Status.class),
            Status.CANCELLED, EnumSet.noneOf(Status.class),
            Status.FAILED, EnumSet.noneOf(Status.class));

    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    @Column(name = "store_id", nullable = false)
    private UUID storeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    private String justification;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_via_ai", nullable = false)
    private boolean createdViaAi;
    @Column(name = "conversation_id")
    private UUID conversationId;
    @Column(name = "executed_at")
    private Instant executedAt;
    @Column(name = "failure_reason")
    private String failureReason;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProposedAction(UUID organizationId, UUID storeId, String justification, UUID createdBy,
            boolean createdViaAi, UUID conversationId, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.type = Type.REPLENISHMENT;
        this.storeId = storeId;
        this.status = Status.DRAFT;
        this.justification = justification;
        this.createdBy = createdBy;
        this.createdViaAi = createdViaAi;
        this.conversationId = conversationId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean canTransitionTo(Status next) {
        return TRANSITIONS.get(status).contains(next);
    }

    /** Aplica a transicao ou lanca 409 se for invalida. */
    public void transitionTo(Status next, Instant now) {
        if (!canTransitionTo(next)) {
            throw ApiException.conflict("INVALID_TRANSITION",
                    "Transicao invalida: " + status + " -> " + next);
        }
        this.status = next;
        this.updatedAt = now;
        if (next == Status.EXECUTED) {
            this.executedAt = now;
        }
    }

    public void fail(String reason, Instant now) {
        transitionTo(Status.FAILED, now);
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 900));
    }

    public void updateJustification(String justification, Instant now) {
        this.justification = justification;
        this.updatedAt = now;
    }

    public boolean isEditable() {
        return status == Status.DRAFT || status == Status.PENDING_APPROVAL;
    }
}
