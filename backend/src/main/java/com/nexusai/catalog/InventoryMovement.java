package com.nexusai.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryMovement {
    public enum Type { OPENING, SALE, REPLENISHMENT, ADJUSTMENT }

    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "store_id", nullable = false)
    private UUID storeId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;
    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;
    @Column(name = "reference_type")
    private String referenceType;
    @Column(name = "reference_id")
    private UUID referenceId;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public InventoryMovement(UUID organizationId, UUID storeId, UUID productId, Type type, int delta, int balanceAfter,
            String referenceType, UUID referenceId, String idempotencyKey, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.productId = productId;
        this.type = type;
        this.quantityDelta = delta;
        this.balanceAfter = balanceAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.idempotencyKey = idempotencyKey;
        this.occurredAt = occurredAt;
    }
}
