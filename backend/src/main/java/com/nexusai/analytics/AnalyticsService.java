package com.nexusai.analytics;

import com.nexusai.analytics.AnalyticsModels.*;
import com.nexusai.common.DateRange;
import com.nexusai.common.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas analiticas (somente leitura) sempre escopadas por organizacao. Usa SQL parametrizado:
 * nenhum valor vindo do cliente ou do modelo de IA e concatenado na consulta.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final JdbcClient jdbc;

    public AnalyticsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Filtros opcionais; todos validados contra a organizacao antes de uso. */
    public record Filters(UUID storeId, String category, UUID productId) {
        public static Filters none() {
            return new Filters(null, null, null);
        }
    }

    // ---------- vendas ----------

    public SalesMetrics salesMetrics(UUID org, DateRange range, Filters f) {
        Query q = baseSales(org, range, f);
        return jdbc.sql("""
                SELECT COALESCE(SUM(i.quantity * i.unit_price), 0) AS revenue,
                       COUNT(DISTINCT s.id) AS cnt,
                       COALESCE(SUM(i.quantity), 0) AS units,
                       COALESCE(SUM(i.quantity * i.unit_cost), 0) AS cost,
                       MIN(i.unit_cost) AS min_cost
                FROM sales s
                JOIN sale_items i ON i.sale_id = s.id
                JOIN products p ON p.id = i.product_id
                """ + q.where)
                .params(q.params)
                .query((rs, n) -> metrics(rs))
                .single();
    }

    private static SalesMetrics metrics(ResultSet rs) throws SQLException {
        BigDecimal revenue = rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP);
        long cnt = rs.getLong("cnt");
        long units = rs.getLong("units");
        BigDecimal cost = rs.getBigDecimal("cost");
        BigDecimal minCost = rs.getBigDecimal("min_cost");
        BigDecimal avg = cnt == 0 ? null : revenue.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP);
        BigDecimal margin = null;
        BigDecimal marginPct = null;
        // Margem so e calculada quando todos os itens possuem custo informado (>0).
        if (cnt > 0 && minCost != null && minCost.signum() > 0) {
            margin = revenue.subtract(cost).setScale(2, RoundingMode.HALF_UP);
            if (revenue.signum() > 0) {
                marginPct = margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, RoundingMode.HALF_UP);
            }
        }
        return new SalesMetrics(revenue, cnt, units, avg, margin, marginPct);
    }

    public PeriodComparison compare(UUID org, DateRange current, Filters f) {
        DateRange prev = current.previous();
        SalesMetrics cur = salesMetrics(org, current, f);
        SalesMetrics old = salesMetrics(org, prev, f);
        String note = null;
        if (old.salesCount() == 0) {
            note = "Sem vendas no periodo anterior: variacao percentual indisponivel (dados insuficientes).";
        }
        return new PeriodComparison(current, prev, cur, old,
                pct(cur.revenue(), old.revenue()),
                pct(BigDecimal.valueOf(cur.salesCount()), BigDecimal.valueOf(old.salesCount())),
                pct(cur.averageTicket(), old.averageTicket()), note);
    }

    static BigDecimal pct(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    public List<SeriesPoint> salesSeries(UUID org, DateRange range, Filters f) {
        Query q = baseSales(org, range, f);
        Map<LocalDate, SeriesPoint> byDate = new HashMap<>();
        jdbc.sql("""
                SELECT s.sale_date AS d, SUM(i.quantity * i.unit_price) AS revenue, COUNT(DISTINCT s.id) AS cnt
                FROM sales s
                JOIN sale_items i ON i.sale_id = s.id
                JOIN products p ON p.id = i.product_id
                """ + q.where + " GROUP BY s.sale_date")
                .params(q.params)
                .query((rs, n) -> new SeriesPoint(rs.getObject("d", LocalDate.class),
                        rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP), rs.getLong("cnt")))
                .list().forEach(p -> byDate.put(p.date(), p));
        List<SeriesPoint> out = new ArrayList<>();
        for (LocalDate d = range.from(); !d.isAfter(range.to()); d = d.plusDays(1)) {
            out.add(byDate.getOrDefault(d, new SeriesPoint(d, BigDecimal.ZERO.setScale(2), 0)));
        }
        return out;
    }

    /** @param orderBy "units" ou "revenue" (qualquer outro valor e rejeitado) */
    public List<TopProduct> topProducts(UUID org, DateRange range, Filters f, int limit, String orderBy) {
        String order = switch (orderBy == null ? "units" : orderBy) {
            case "units" -> "units DESC, revenue DESC";
            case "revenue" -> "revenue DESC, units DESC";
            default -> throw ApiException.badRequest("orderBy deve ser 'units' ou 'revenue'");
        };
        Query q = baseSales(org, range, f);
        return jdbc.sql("""
                SELECT p.id, p.sku, p.name, p.category, SUM(i.quantity) AS units,
                       SUM(i.quantity * i.unit_price) AS revenue
                FROM sales s
                JOIN sale_items i ON i.sale_id = s.id
                JOIN products p ON p.id = i.product_id
                """ + q.where + " GROUP BY p.id, p.sku, p.name, p.category ORDER BY " + order + ", p.name LIMIT :limit")
                .params(q.params)
                .param("limit", clamp(limit))
                .query((rs, n) -> new TopProduct(rs.getObject("id", UUID.class), rs.getString("sku"),
                        rs.getString("name"), rs.getString("category"), rs.getLong("units"),
                        rs.getBigDecimal("revenue").setScale(2, RoundingMode.HALF_UP)))
                .list();
    }

    // ---------- estoque ----------

    public List<LowStockItem> lowStock(UUID org, UUID storeId, int limit) {
        assertStore(org, storeId);
        StringBuilder sql = new StringBuilder("""
                SELECT b.store_id, st.name AS store_name, p.id AS pid, p.sku, p.name, p.category,
                       b.quantity, p.min_stock
                FROM inventory_balances b
                JOIN products p ON p.id = b.product_id
                JOIN stores st ON st.id = b.store_id
                WHERE b.organization_id = :org AND p.active = TRUE AND b.quantity < p.min_stock
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        if (storeId != null) {
            sql.append(" AND b.store_id = :store");
            params.put("store", storeId);
        }
        sql.append(" ORDER BY (p.min_stock - b.quantity) DESC, p.name, st.name LIMIT :limit");
        params.put("limit", clamp(limit));
        return jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> new LowStockItem(rs.getObject("store_id", UUID.class), rs.getString("store_name"),
                        rs.getObject("pid", UUID.class), rs.getString("sku"), rs.getString("name"),
                        rs.getString("category"), rs.getInt("quantity"), rs.getInt("min_stock"),
                        rs.getInt("min_stock") - rs.getInt("quantity")))
                .list();
    }

    /**
     * Cobertura = saldo / media diaria de vendas na janela. Janela = ultimos {@code windowDays} dias
     * terminando em {@code today} (inclusive). Cobertura nula quando nao houve vendas na janela.
     * Ordenado do menor para o maior (itens sem vendas por ultimo).
     */
    public List<CoverageItem> coverage(UUID org, UUID storeId, UUID productId, LocalDate today, int windowDays,
            int limit) {
        assertStore(org, storeId);
        if (windowDays < 1 || windowDays > 180) {
            throw ApiException.badRequest("windowDays deve estar entre 1 e 180");
        }
        LocalDate from = today.minusDays(windowDays - 1L);
        StringBuilder sql = new StringBuilder("""
                SELECT b.store_id, st.name AS store_name, p.id AS pid, p.sku, p.name, p.category, b.quantity,
                       p.min_stock, p.lead_time_days, p.target_coverage_days, COALESCE(u.units, 0) AS units
                FROM inventory_balances b
                JOIN products p ON p.id = b.product_id
                JOIN stores st ON st.id = b.store_id
                LEFT JOIN (
                    SELECT s.store_id AS sid, i.product_id AS pid, SUM(i.quantity) AS units
                    FROM sale_items i JOIN sales s ON s.id = i.sale_id
                    WHERE s.organization_id = :org AND s.sale_date BETWEEN :from AND :to
                    GROUP BY s.store_id, i.product_id
                ) u ON u.sid = b.store_id AND u.pid = b.product_id
                WHERE b.organization_id = :org AND p.active = TRUE
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        params.put("from", from);
        params.put("to", today);
        if (storeId != null) {
            sql.append(" AND b.store_id = :store");
            params.put("store", storeId);
        }
        if (productId != null) {
            sql.append(" AND p.id = :prod");
            params.put("prod", productId);
        }
        List<CoverageItem> rows = jdbc.sql(sql.toString()).params(params).query((rs, n) -> {
            long units = rs.getLong("units");
            int qty = rs.getInt("quantity");
            BigDecimal avg = BigDecimal.valueOf(units).divide(BigDecimal.valueOf(windowDays), 3, RoundingMode.HALF_UP);
            BigDecimal cov = avg.signum() == 0 ? null
                    : BigDecimal.valueOf(qty).divide(avg, 1, RoundingMode.HALF_UP);
            return new CoverageItem(rs.getObject("store_id", UUID.class), rs.getString("store_name"),
                    rs.getObject("pid", UUID.class), rs.getString("sku"), rs.getString("name"),
                    rs.getString("category"), qty, rs.getInt("min_stock"), units, avg, cov,
                    rs.getInt("lead_time_days"), rs.getInt("target_coverage_days"));
        }).list();
        return rows.stream()
                .sorted(Comparator.comparing(CoverageItem::coverageDays, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CoverageItem::productName))
                .limit(clamp(limit)).toList();
    }

    /** Produtos com estoque positivo e sem vendas desde {@code cutoff} (ou nunca vendidos). */
    public List<StaleProduct> staleProducts(UUID org, UUID storeId, LocalDate cutoff, int limit) {
        assertStore(org, storeId);
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.sku, p.name, p.category, SUM(b.quantity) AS stock, MAX(ls.d) AS last_sale
                FROM products p
                JOIN inventory_balances b ON b.product_id = p.id
                LEFT JOIN (
                    SELECT i.product_id AS pid, MAX(s.sale_date) AS d
                    FROM sale_items i JOIN sales s ON s.id = i.sale_id
                    WHERE s.organization_id = :org
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        if (storeId != null) {
            sql.append(" AND s.store_id = :store");
            params.put("store", storeId);
        }
        sql.append("""
                    GROUP BY i.product_id
                ) ls ON ls.pid = p.id
                WHERE p.organization_id = :org AND p.active = TRUE
                """);
        if (storeId != null) {
            sql.append(" AND b.store_id = :store");
        }
        sql.append("""
                GROUP BY p.id, p.sku, p.name, p.category
                HAVING SUM(b.quantity) > 0 AND (MAX(ls.d) IS NULL OR MAX(ls.d) < :cutoff)
                ORDER BY stock DESC, p.name LIMIT :limit
                """);
        params.put("cutoff", cutoff);
        params.put("limit", clamp(limit));
        return jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> new StaleProduct(rs.getObject("id", UUID.class), rs.getString("sku"),
                        rs.getString("name"), rs.getString("category"), rs.getLong("stock"),
                        rs.getObject("last_sale", LocalDate.class)))
                .list();
    }

    // ---------- util ----------

    public void assertStore(UUID org, UUID storeId) {
        if (storeId == null) {
            return;
        }
        Integer n = jdbc.sql("SELECT COUNT(*) FROM stores WHERE id = :id AND organization_id = :org")
                .param("id", storeId).param("org", org).query(Integer.class).single();
        if (n == 0) {
            throw ApiException.notFound("Loja");
        }
    }

    public void assertProduct(UUID org, UUID productId) {
        if (productId == null) {
            return;
        }
        Integer n = jdbc.sql("SELECT COUNT(*) FROM products WHERE id = :id AND organization_id = :org")
                .param("id", productId).param("org", org).query(Integer.class).single();
        if (n == 0) {
            throw ApiException.notFound("Produto");
        }
    }

    private Query baseSales(UUID org, DateRange range, Filters f) {
        assertStore(org, f.storeId());
        assertProduct(org, f.productId());
        StringBuilder where = new StringBuilder(
                " WHERE s.organization_id = :org AND s.sale_date BETWEEN :from AND :to");
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        params.put("from", range.from());
        params.put("to", range.to());
        if (f.storeId() != null) {
            where.append(" AND s.store_id = :store");
            params.put("store", f.storeId());
        }
        if (f.category() != null && !f.category().isBlank()) {
            where.append(" AND p.category = :cat");
            params.put("cat", f.category());
        }
        if (f.productId() != null) {
            where.append(" AND p.id = :prod");
            params.put("prod", f.productId());
        }
        return new Query(where.toString(), params);
    }

    private static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }

    private record Query(String where, Map<String, Object> params) {}
}
