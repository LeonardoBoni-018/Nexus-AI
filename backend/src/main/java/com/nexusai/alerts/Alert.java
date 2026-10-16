package com.nexusai.alerts;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alert {

    public enum Type { LOW_STOCK, STOCKOUT_RISK, SALES_DROP, NO_MOVEMENT }

    public enum Severity { INFO, WARNING, CRITICAL }

    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;
    @Column(name = "store_id")
    private UUID storeId;
    @Column(name = "product_id")
    private UUID productId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String message;
    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(nullable = false)
    private boolean resolved;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Alert(UUID organizationId, Type type, Severity severity, UUID storeId, UUID productId, String title,
            String message, String dedupeKey, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.type = type;
        this.severity = severity;
        this.storeId = storeId;
        this.productId = productId;
        this.title = title;
        this.message = message;
        this.dedupeKey = dedupeKey;
        this.createdAt = now;
    }

    /** Atualiza conteudo de um alerta persistente; reabre (nao lido) se estava resolvido. */
    void refresh(Severity severity, String title, String message) {
        if (resolved) {
            this.resolved = false;
            this.readAt = null;
        }
        this.severity = severity;
        this.title = title;
        this.message = message;
    }

    void resolve() {
        this.resolved = true;
    }

    void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }
}
