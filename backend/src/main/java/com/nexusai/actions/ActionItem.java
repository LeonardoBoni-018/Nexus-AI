package com.nexusai.actions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "action_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionItem {

    @Id
    private UUID id;
    @Column(name = "action_id", nullable = false)
    private UUID actionId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "current_stock", nullable = false)
    private int currentStock;
    @Column(name = "avg_daily_sales", nullable = false)
    private BigDecimal avgDailySales;
    @Column(name = "coverage_days")
    private BigDecimal coverageDays;
    @Column(name = "suggested_quantity", nullable = false)
    private int suggestedQuantity;
    @Column(name = "approved_quantity", nullable = false)
    private int approvedQuantity;

    public ActionItem(UUID actionId, UUID productId, int currentStock, BigDecimal avgDailySales,
            BigDecimal coverageDays, int suggestedQuantity) {
        this.id = UUID.randomUUID();
        this.actionId = actionId;
        this.productId = productId;
        this.currentStock = currentStock;
        this.avgDailySales = avgDailySales;
        this.coverageDays = coverageDays;
        this.suggestedQuantity = suggestedQuantity;
        this.approvedQuantity = suggestedQuantity;
    }

    public void setApprovedQuantity(int quantity) {
        this.approvedQuantity = quantity;
    }
}
