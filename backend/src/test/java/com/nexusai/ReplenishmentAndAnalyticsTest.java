package com.nexusai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexusai.actions.ReplenishmentPlanner;
import com.nexusai.alerts.AlertService;
import com.nexusai.analytics.AnalyticsModels.PeriodComparison;
import com.nexusai.analytics.AnalyticsService;
import com.nexusai.analytics.AnalyticsService.Filters;
import com.nexusai.common.DateRange;
import com.nexusai.common.PeriodResolver;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReplenishmentAndAnalyticsTest extends IntegrationTestBase {

    @Autowired
    AnalyticsService analytics;
    @Autowired
    ReplenishmentPlanner planner;
    @Autowired
    PeriodResolver periods;
    @Autowired
    AlertService alerts;

    @Test
    void seedMovementsMatchBalancesAndNothingIsNegative() {
        int mismatches = jdbc.sql("""
                SELECT COUNT(*) FROM inventory_balances b WHERE b.quantity <>
                (SELECT COALESCE(SUM(m.quantity_delta),0) FROM inventory_movements m
                 WHERE m.store_id = b.store_id AND m.product_id = b.product_id)
                """).query(Integer.class).single();
        assertEquals(0, mismatches);
        int negative = jdbc.sql("SELECT COUNT(*) FROM inventory_movements WHERE balance_after < 0").query(Integer.class).single();
        assertEquals(0, negative);
    }

    @Test
    void saleTotalsEqualItemTotals() {
        int bad = jdbc.sql("""
                SELECT COUNT(*) FROM sales s WHERE s.total <>
                (SELECT SUM(i.quantity * i.unit_price) FROM sale_items i WHERE i.sale_id = s.id)
                """).query(Integer.class).single();
        assertEquals(0, bad);
    }

    @Test
    void comparisonUsesEqualLengthAdjacentPeriodsAndSumsMatch() {
        UUID org = orgId("mercado-aurora-demo");
        DateRange cur = DateRange.of(periods.today().minusDays(6), periods.today(), 400);
        PeriodComparison cmp = analytics.compare(org, cur, Filters.none());
        assertEquals(cur.days(), cmp.previous().days());
        assertEquals(cur.from().minusDays(1), cmp.previous().to());
        BigDecimal seriesSum = analytics.salesSeries(org, cur, Filters.none()).stream()
                .map(p -> p.revenue()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, seriesSum.compareTo(cmp.currentMetrics().revenue()));
        assertNotNull(cmp.revenueVariationPercent());
        assertNotNull(cmp.currentMetrics().estimatedMargin(), "todos os itens tem custo: margem deve existir");
    }

    @Test
    void emptyPreviousPeriodYieldsNullVariationNotFakeNumbers() {
        UUID org = orgId("casa-e-cia-demo");
        DateRange old = DateRange.of(periods.today().minusDays(400), periods.today().minusDays(390), 400);
        PeriodComparison cmp = analytics.compare(org, old, Filters.none());
        assertEquals(0, cmp.currentMetrics().salesCount());
        assertEquals(null, cmp.revenueVariationPercent());
        assertEquals(null, cmp.currentMetrics().averageTicket());
        assertNotNull(cmp.note());
    }

    @Test
    void replenishmentTargetFormulaIsDeterministic() {
        UUID org = orgId("mercado-aurora-demo");
        UUID store = storeId(org, "Loja Centro");
        var lines = planner.plan(org, store, null);
        assertFalse(lines.isEmpty());
        for (var l : lines) {
            assertTrue(l.suggestedQuantity() > 0);
            assertEquals(l.targetStock() - l.currentStock(), l.suggestedQuantity());
            assertTrue(l.targetStock() >= l.minStock());
        }
        // mesma entrada, mesma saida
        assertEquals(lines, planner.plan(org, store, null));
    }

    @Test
    void alertsAreGeneratedFromRulesAndEvaluationIsIdempotent() {
        UUID org = orgId("mercado-aurora-demo");
        alerts.evaluate(org);
        long unread = alerts.unreadCount(org);
        assertTrue(unread > 0);
        assertEquals(0, alerts.evaluate(org), "reavaliar sem mudanca de dados nao deve criar nem resolver alertas");
        int lowStock = jdbc.sql("SELECT COUNT(*) FROM alerts WHERE organization_id = :o AND type = 'LOW_STOCK' AND resolved = FALSE")
                .param("o", org).query(Integer.class).single();
        assertTrue(lowStock >= 3);
        int noMovement = jdbc.sql("SELECT COUNT(*) FROM alerts WHERE organization_id = :o AND type = 'NO_MOVEMENT'")
                .param("o", org).query(Integer.class).single();
        assertTrue(noMovement >= 1);
    }
}
