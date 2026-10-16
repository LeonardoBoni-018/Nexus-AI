package com.nexusai.sales;

import com.nexusai.analytics.AnalyticsService;
import com.nexusai.common.DateRange;
import com.nexusai.common.PageResponse;
import com.nexusai.common.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SalesQueryService {

    public record SaleRow(UUID id, Instant occurredAt, LocalDate saleDate, UUID storeId, String storeName,
            BigDecimal total, long itemsCount) {}

    public record SaleItemRow(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice,
            BigDecimal lineTotal) {}

    public record SaleDetail(SaleRow sale, List<SaleItemRow> items) {}

    private final JdbcClient jdbc;
    private final AnalyticsService analytics;

    public SalesQueryService(JdbcClient jdbc, AnalyticsService analytics) {
        this.jdbc = jdbc;
        this.analytics = analytics;
    }

    public PageResponse<SaleRow> list(UUID org, DateRange range, UUID storeId, int page, int size) {
        analytics.assertStore(org, storeId);
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        params.put("from", range.from());
        params.put("to", range.to());
        StringBuilder where = new StringBuilder(
                " WHERE s.organization_id = :org AND s.sale_date BETWEEN :from AND :to");
        if (storeId != null) {
            where.append(" AND s.store_id = :store");
            params.put("store", storeId);
        }
        long total = jdbc.sql("SELECT COUNT(*) FROM sales s" + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<SaleRow> items = jdbc.sql("""
                SELECT s.id, s.occurred_at, s.sale_date, s.store_id, st.name AS store_name, s.total,
                       (SELECT COALESCE(SUM(i.quantity), 0) FROM sale_items i WHERE i.sale_id = s.id) AS items_count
                FROM sales s JOIN stores st ON st.id = s.store_id
                """ + where + " ORDER BY s.occurred_at DESC, s.id LIMIT :limit OFFSET :offset")
                .params(params).query((rs, n) -> row(rs)).list();
        return PageResponse.of(items, page, size, total);
    }

    public SaleDetail detail(UUID org, UUID id) {
        SaleRow sale = jdbc.sql("""
                SELECT s.id, s.occurred_at, s.sale_date, s.store_id, st.name AS store_name, s.total,
                       (SELECT COALESCE(SUM(i.quantity), 0) FROM sale_items i WHERE i.sale_id = s.id) AS items_count
                FROM sales s JOIN stores st ON st.id = s.store_id
                WHERE s.id = :id AND s.organization_id = :org
                """).param("id", id).param("org", org).query((rs, n) -> row(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Venda"));
        List<SaleItemRow> items = jdbc.sql("""
                SELECT p.id, p.sku, p.name, i.quantity, i.unit_price
                FROM sale_items i JOIN products p ON p.id = i.product_id WHERE i.sale_id = :id ORDER BY p.name
                """).param("id", id)
                .query((rs, n) -> new SaleItemRow(rs.getObject("id", UUID.class), rs.getString("sku"),
                        rs.getString("name"), rs.getInt("quantity"), rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("unit_price").multiply(BigDecimal.valueOf(rs.getInt("quantity")))))
                .list();
        return new SaleDetail(sale, items);
    }

    private static SaleRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SaleRow(rs.getObject("id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getObject("sale_date", LocalDate.class), rs.getObject("store_id", UUID.class),
                rs.getString("store_name"), rs.getBigDecimal("total"), rs.getLong("items_count"));
    }
}
