package com.nexusai.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    private String details;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AuditEvent(UUID organizationId, UUID userId, String eventType, String entityType, String entityId,
            String details, String requestId, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.userId = userId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details == null ? null : (details.length() > 1900 ? details.substring(0, 1900) : details);
        this.requestId = requestId;
        this.createdAt = now;
    }
}
