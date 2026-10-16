package com.nexusai.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory_balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryBalance {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "store_id", nullable = false)
    private UUID storeId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(nullable = false)
    private int quantity;
    @Version
    private long version;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public InventoryBalance(UUID organizationId, UUID storeId, UUID productId, int quantity, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.productId = productId;
        this.quantity = quantity;
        this.updatedAt = now;
    }

    /** Aplica a variacao; nunca permite saldo negativo. */
    void apply(int delta, Instant now) {
        int next = quantity + delta;
        if (next < 0) {
            throw new IllegalStateException("Estoque insuficiente: saldo " + quantity + ", variacao " + delta);
        }
        this.quantity = next;
        this.updatedAt = now;
    }
}
