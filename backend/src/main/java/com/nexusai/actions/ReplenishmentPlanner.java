package com.nexusai.actions;

import com.nexusai.analytics.AnalyticsModels.CoverageItem;
import com.nexusai.analytics.AnalyticsService;
import com.nexusai.common.PeriodResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculo DETERMINISTICO de reposicao. A IA nunca define quantidades: ela apenas aciona este calculo.
 *
 * <pre>
 * estoqueAlvo = max(estoqueMinimo, ceil(mediaDiaria * (coberturaAlvoDias + leadTimeDias)))
 * sugerido    = max(0, estoqueAlvo - saldoAtual)
 * </pre>
 * Media diaria = unidades vendidas na janela de 28 dias / 28.
 */
@Service
public class ReplenishmentPlanner {

    public static final int WINDOW_DAYS = 28;

    public record Line(UUID productId, String sku, String productName, int currentStock, int minStock,
            BigDecimal averageDailySales, BigDecimal coverageDays, int targetStock, int suggestedQuantity) {}

    private final AnalyticsService analytics;
    private final PeriodResolver periods;

    public ReplenishmentPlanner(AnalyticsService analytics, PeriodResolver periods) {
        this.analytics = analytics;
        this.periods = periods;
    }

    /** @param productIds filtro opcional; nulo/vazio considera todos os produtos da loja */
    @Transactional(readOnly = true)
    public List<Line> plan(UUID org, UUID storeId, Set<UUID> productIds) {
        List<CoverageItem> rows = analytics.coverage(org, storeId, null, periods.today(), WINDOW_DAYS, 100);
        return rows.stream()
                .filter(r -> productIds == null || productIds.isEmpty() || productIds.contains(r.productId()))
                .map(r -> toLine(r))
                .filter(l -> l.suggestedQuantity() > 0)
                .toList();
    }

    static Line toLine(CoverageItem r) {
        int target = targetStock(r.averageDailySales(), r.minStock(), r.targetCoverageDays(), r.leadTimeDays());
        int suggested = Math.max(0, target - r.quantity());
        return new Line(r.productId(), r.sku(), r.productName(), r.quantity(), r.minStock(), r.averageDailySales(),
                r.coverageDays(), target, suggested);
    }

    static int targetStock(BigDecimal avgDaily, int minStock, int targetDays, int leadDays) {
        int demand = avgDaily.multiply(BigDecimal.valueOf((long) targetDays + leadDays))
                .setScale(0, RoundingMode.CEILING).intValueExact();
        return Math.max(minStock, demand);
    }
}
