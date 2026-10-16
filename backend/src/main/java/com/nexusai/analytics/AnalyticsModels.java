package com.nexusai.analytics;

import com.nexusai.common.DateRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Contratos de leitura analitica, compartilhados entre a API REST e as ferramentas da IA. */
public final class AnalyticsModels {

    private AnalyticsModels() {}

    /** margin* sao nulos quando algum item vendido nao possui custo (dados insuficientes). */
    public record SalesMetrics(BigDecimal revenue, long salesCount, long itemsSold, BigDecimal averageTicket,
            BigDecimal estimatedMargin, BigDecimal marginPercent) {}

    /** variationPercent nulo quando o periodo anterior nao possui base de comparacao. */
    public record PeriodComparison(DateRange current, DateRange previous, SalesMetrics currentMetrics,
            SalesMetrics previousMetrics, BigDecimal revenueVariationPercent, BigDecimal salesCountVariationPercent,
            BigDecimal averageTicketVariationPercent, String note) {}

    public record SeriesPoint(LocalDate date, BigDecimal revenue, long salesCount) {}

    public record TopProduct(UUID productId, String sku, String name, String category, long unitsSold,
            BigDecimal revenue) {}

    public record LowStockItem(UUID storeId, String storeName, UUID productId, String sku, String productName,
            String category, int quantity, int minStock, int deficit) {}

    public record CoverageItem(UUID storeId, String storeName, UUID productId, String sku, String productName,
            String category, int quantity, int minStock, long unitsSoldInWindow, BigDecimal averageDailySales,
            BigDecimal coverageDays, int leadTimeDays, int targetCoverageDays) {}

    public record StaleProduct(UUID productId, String sku, String name, String category, long totalStock,
            LocalDate lastSaleDate) {}
}
