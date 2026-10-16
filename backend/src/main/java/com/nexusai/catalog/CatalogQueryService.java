package com.nexusai.catalog;

import com.nexusai.analytics.AnalyticsService;
import com.nexusai.common.PageResponse;
import com.nexusai.common.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    public record StoreDto(UUID id, String name, String city) {}

    public record ProductRow(UUID id, String sku, String name, String category, BigDecimal cost, BigDecimal price,
            int minStock, int targetCoverageDays, int leadTimeDays, boolean active, long totalStock) {}

    public record StoreBalance(UUID storeId, String storeName, int quantity) {}

    public record ProductDetail(ProductRow product, List<StoreBalance> balances) {}

    public record InventoryRow(UUID storeId, String storeName, UUID productId, String sku, String productName,
            String category, int quantity, int minStock, boolean belowMinimum) {}

    public record MovementRow(UUID id, Instant occurredAt, UUID storeId, String storeName, UUID productId, String sku,
            String productName, String type, int quantityDelta, int balanceAfter, String referenceType,
            UUID referenceId) {}

    private final JdbcClient jdbc;
    private final AnalyticsService analytics;

    public CatalogQueryService(JdbcClient jdbc, AnalyticsService analytics) {
        this.jdbc = jdbc;
        this.analytics = analytics;
    }

    public List<StoreDto> stores(UUID org) {
        return jdbc.sql("SELECT id, name, city FROM stores WHERE organization_id = :org ORDER BY name")
                .param("org", org)
                .query((rs, n) -> new StoreDto(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("city")))
                .list();
    }

    public List<String> categories(UUID org) {
        return jdbc.sql("SELECT DISTINCT category FROM products WHERE organization_id = :org ORDER BY category")
                .param("org", org).query(String.class).list();
    }

    public PageResponse<ProductRow> products(UUID org, String search, String category, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        StringBuilder where = new StringBuilder(" WHERE p.organization_id = :org");
        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(p.name) LIKE :q OR LOWER(p.sku) LIKE :q)");
            params.put("q", "%" + escapeLike(search.trim().toLowerCase()) + "%");
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND p.category = :cat");
            params.put("cat", category);
        }
        long total = jdbc.sql("SELECT COUNT(*) FROM products p" + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<ProductRow> items = jdbc.sql("""
                SELECT p.id, p.sku, p.name, p.category, p.cost, p.price, p.min_stock, p.target_coverage_days,
                       p.lead_time_days, p.active,
                       COALESCE((SELECT SUM(b.quantity) FROM inventory_balances b WHERE b.product_id = p.id), 0) AS stock
                FROM products p
                """ + where + " ORDER BY p.name LIMIT :limit OFFSET :offset")
                .params(params).query((rs, n) -> productRow(rs)).list();
        return PageResponse.of(items, page, size, total);
    }

    public ProductDetail product(UUID org, UUID id) {
        ProductRow row = jdbc.sql("""
                SELECT p.id, p.sku, p.name, p.category, p.cost, p.price, p.min_stock, p.target_coverage_days,
                       p.lead_time_days, p.active,
                       COALESCE((SELECT SUM(b.quantity) FROM inventory_balances b WHERE b.product_id = p.id), 0) AS stock
                FROM products p WHERE p.id = :id AND p.organization_id = :org
                """).param("id", id).param("org", org).query((rs, n) -> productRow(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Produto"));
        List<StoreBalance> balances = jdbc.sql("""
                SELECT st.id, st.name, b.quantity FROM inventory_balances b JOIN stores st ON st.id = b.store_id
                WHERE b.product_id = :id AND b.organization_id = :org ORDER BY st.name
                """).param("id", id).param("org", org)
                .query((rs, n) -> new StoreBalance(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getInt("quantity"))).list();
        return new ProductDetail(row, balances);
    }

    public PageResponse<InventoryRow> inventory(UUID org, UUID storeId, String search, boolean lowOnly, int page,
            int size) {
        analytics.assertStore(org, storeId);
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        StringBuilder where = new StringBuilder(" WHERE b.organization_id = :org");
        if (storeId != null) {
            where.append(" AND b.store_id = :store");
            params.put("store", storeId);
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(p.name) LIKE :q OR LOWER(p.sku) LIKE :q)");
            params.put("q", "%" + escapeLike(search.trim().toLowerCase()) + "%");
        }
        if (lowOnly) {
            where.append(" AND b.quantity < p.min_stock");
        }
        String from = " FROM inventory_balances b JOIN products p ON p.id = b.product_id JOIN stores st ON st.id = b.store_id";
        long total = jdbc.sql("SELECT COUNT(*)" + from + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<InventoryRow> items = jdbc.sql("""
                SELECT st.id AS store_id, st.name AS store_name, p.id AS pid, p.sku, p.name, p.category,
                       b.quantity, p.min_stock
                """ + from + where + " ORDER BY p.name, st.name LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> new InventoryRow(rs.getObject("store_id", UUID.class), rs.getString("store_name"),
                        rs.getObject("pid", UUID.class), rs.getString("sku"), rs.getString("name"),
                        rs.getString("category"), rs.getInt("quantity"), rs.getInt("min_stock"),
                        rs.getInt("quantity") < rs.getInt("min_stock")))
                .list();
        return PageResponse.of(items, page, size, total);
    }

    public PageResponse<MovementRow> movements(UUID org, UUID storeId, UUID productId, String type, int page, int size) {
        analytics.assertStore(org, storeId);
        analytics.assertProduct(org, productId);
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        StringBuilder where = new StringBuilder(" WHERE m.organization_id = :org");
        if (storeId != null) {
            where.append(" AND m.store_id = :store");
            params.put("store", storeId);
        }
        if (productId != null) {
            where.append(" AND m.product_id = :prod");
            params.put("prod", productId);
        }
        if (type != null && !type.isBlank()) {
            try {
                params.put("type", InventoryMovement.Type.valueOf(type.toUpperCase()).name());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Tipo de movimento invalido");
            }
            where.append(" AND m.type = :type");
        }
        String from = " FROM inventory_movements m JOIN products p ON p.id = m.product_id JOIN stores st ON st.id = m.store_id";
        long total = jdbc.sql("SELECT COUNT(*)" + from + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<MovementRow> items = jdbc.sql("""
                SELECT m.id, m.occurred_at, st.id AS store_id, st.name AS store_name, p.id AS pid, p.sku, p.name,
                       m.type, m.quantity_delta, m.balance_after, m.reference_type, m.reference_id
                """ + from + where + " ORDER BY m.occurred_at DESC, m.id LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> new MovementRow(rs.getObject("id", UUID.class),
                        rs.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject("store_id", UUID.class), rs.getString("store_name"),
                        rs.getObject("pid", UUID.class), rs.getString("sku"), rs.getString("name"),
                        rs.getString("type"), rs.getInt("quantity_delta"), rs.getInt("balance_after"),
                        rs.getString("reference_type"), rs.getObject("reference_id", UUID.class)))
                .list();
        return PageResponse.of(items, page, size, total);
    }

    private static ProductRow productRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProductRow(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("name"),
                rs.getString("category"), rs.getBigDecimal("cost"), rs.getBigDecimal("price"), rs.getInt("min_stock"),
                rs.getInt("target_coverage_days"), rs.getInt("lead_time_days"), rs.getBoolean("active"),
                rs.getLong("stock"));
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
