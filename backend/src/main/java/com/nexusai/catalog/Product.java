package com.nexusai.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(nullable = false)
    private String sku;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private BigDecimal cost;
    @Column(nullable = false)
    private BigDecimal price;
    @Column(name = "min_stock", nullable = false)
    private int minStock;
    @Column(name = "target_coverage_days", nullable = false)
    private int targetCoverageDays;
    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Product(UUID organizationId, String sku, String name, String category, BigDecimal cost, BigDecimal price,
            int minStock, int targetCoverageDays, int leadTimeDays, Instant now) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.cost = cost;
        this.price = price;
        this.minStock = minStock;
        this.targetCoverageDays = targetCoverageDays;
        this.leadTimeDays = leadTimeDays;
        this.createdAt = now;
    }
}
