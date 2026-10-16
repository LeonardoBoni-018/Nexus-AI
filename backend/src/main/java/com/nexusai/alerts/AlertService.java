package com.nexusai.alerts;

import com.nexusai.alerts.Alert.Severity;
import com.nexusai.alerts.Alert.Type;
import com.nexusai.analytics.AnalyticsModels.PeriodComparison;
import com.nexusai.analytics.AnalyticsService;
import com.nexusai.analytics.AnalyticsService.Filters;
import com.nexusai.common.DateRange;
import com.nexusai.common.PeriodResolver;
import com.nexusai.common.exception.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertas deterministicos (sem IA). Regras e limiares explicitos:
 * LOW_STOCK (saldo menor que o minimo), STOCKOUT_RISK (cobertura menor ou igual ao lead time),
 * SALES_DROP (queda de 20% ou mais em 7 dias, com amostra minima de 20 vendas no periodo anterior) e
 * NO_MOVEMENT (sem vendas ha 30 dias com estoque positivo).
 */
@Service
public class AlertService {

    public static final int COVERAGE_WINDOW_DAYS = 28;
    public static final int SALES_DROP_WINDOW_DAYS = 7;
    public static final long SALES_DROP_MIN_SAMPLE = 20;
    public static final BigDecimal SALES_DROP_WARNING = new BigDecimal("-20");
    public static final BigDecimal SALES_DROP_CRITICAL = new BigDecimal("-35");
    public static final int NO_MOVEMENT_DAYS = 30;

    public interface AlertRepository extends JpaRepository<Alert, UUID> {
        List<Alert> findByOrganizationId(UUID organizationId);

        Optional<Alert> findByIdAndOrganizationId(UUID id, UUID organizationId);

        @Query("select a from Alert a where a.organizationId = :org and a.resolved = false "
                + "and (:unreadOnly = false or a.readAt is null) order by "
                + "case a.severity when com.nexusai.alerts.Alert.Severity.CRITICAL then 0 "
                + "when com.nexusai.alerts.Alert.Severity.WARNING then 1 else 2 end, a.createdAt desc")
        Page<Alert> findActive(UUID org, boolean unreadOnly, Pageable pageable);

        @Query("select count(a) from Alert a where a.organizationId = :org and a.resolved = false and a.readAt is null")
        long countUnread(UUID org);
    }

    private record Candidate(Type type, Severity severity, UUID storeId, UUID productId, String title, String message) {}

    private final AlertRepository repository;
    private final AnalyticsService analytics;
    private final PeriodResolver periods;
    private final Clock clock;
    private final JdbcClient jdbc;

    public AlertService(AlertRepository repository, AnalyticsService analytics, PeriodResolver periods, Clock clock,
            JdbcClient jdbc) {
        this.repository = repository;
        this.analytics = analytics;
        this.periods = periods;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Transactional
    public int evaluate(UUID org) {
        LocalDate today = periods.today();
        Map<String, Candidate> found = new HashMap<>();

        analytics.lowStock(org, null, 100).forEach(i -> found.put("LOW_STOCK:" + i.storeId() + ":" + i.productId(),
                new Candidate(Type.LOW_STOCK, i.quantity() == 0 ? Severity.CRITICAL : Severity.WARNING, i.storeId(),
                        i.productId(), "Estoque abaixo do minimo: " + i.productName(),
                        i.productName() + " em " + i.storeName() + ": " + i.quantity() + " un. (minimo "
                                + i.minStock() + ")")));

        analytics.coverage(org, null, null, today, COVERAGE_WINDOW_DAYS, 100).forEach(c -> {
            if (c.coverageDays() != null && c.quantity() >= c.minStock()
                    && c.coverageDays().compareTo(BigDecimal.valueOf(c.leadTimeDays())) <= 0) {
                found.put("STOCKOUT_RISK:" + c.storeId() + ":" + c.productId(),
                        new Candidate(Type.STOCKOUT_RISK,
                                c.coverageDays().compareTo(BigDecimal.valueOf(3)) <= 0 ? Severity.CRITICAL
                                        : Severity.WARNING,
                                c.storeId(), c.productId(), "Risco de ruptura: " + c.productName(),
                                c.productName() + " em " + c.storeName() + ": cobertura de " + c.coverageDays()
                                        + " dias (reposicao leva " + c.leadTimeDays() + " dias)"));
            }
        });

        DateRange current = new DateRange(today.minusDays(SALES_DROP_WINDOW_DAYS - 1L), today);
        PeriodComparison cmp = analytics.compare(org, current, Filters.none());
        if (cmp.previousMetrics().salesCount() >= SALES_DROP_MIN_SAMPLE && cmp.revenueVariationPercent() != null
                && cmp.revenueVariationPercent().compareTo(SALES_DROP_WARNING) <= 0) {
            found.put("SALES_DROP:ORG", new Candidate(Type.SALES_DROP,
                    cmp.revenueVariationPercent().compareTo(SALES_DROP_CRITICAL) <= 0 ? Severity.CRITICAL
                            : Severity.WARNING,
                    null, null, "Queda de faturamento nos ultimos 7 dias",
                    "Faturamento variou " + cmp.revenueVariationPercent() + "% frente aos 7 dias anteriores ("
                            + cmp.currentMetrics().revenue() + " vs " + cmp.previousMetrics().revenue() + " BRL)"));
        }

        analytics.staleProducts(org, null, today.minusDays(NO_MOVEMENT_DAYS), 50).forEach(s -> found.put(
                "NO_MOVEMENT:" + s.productId(),
                new Candidate(Type.NO_MOVEMENT, Severity.INFO, null, s.productId(),
                        "Sem movimentacao: " + s.name(),
                        s.name() + " tem " + s.totalStock() + " un. em estoque e "
                                + (s.lastSaleDate() == null ? "nunca vendeu"
                                        : "ultima venda em " + s.lastSaleDate()))));

        Instant now = Instant.now(clock);
        Map<String, Alert> existing = new HashMap<>();
        repository.findByOrganizationId(org).forEach(a -> existing.put(a.getDedupeKey(), a));
        int changes = 0;
        for (var e : found.entrySet()) {
            Candidate c = e.getValue();
            Alert a = existing.get(e.getKey());
            if (a == null) {
                repository.save(new Alert(org, c.type(), c.severity(), c.storeId(), c.productId(), c.title(),
                        c.message(), e.getKey(), now));
                changes++;
            } else {
                a.refresh(c.severity(), c.title(), c.message());
            }
        }
        for (var e : existing.entrySet()) {
            if (!found.containsKey(e.getKey()) && !e.getValue().isResolved()) {
                e.getValue().resolve();
                changes++;
            }
        }
        return changes;
    }

    @Transactional(readOnly = true)
    public Page<Alert> active(UUID org, boolean unreadOnly, Pageable pageable) {
        return repository.findActive(org, unreadOnly, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID org) {
        return repository.countUnread(org);
    }

    @Transactional(readOnly = true)
    public Alert get(UUID org, UUID id) {
        return repository.findByIdAndOrganizationId(id, org).orElseThrow(() -> ApiException.notFound("Alerta"));
    }

    @Transactional
    public Alert markRead(UUID org, UUID id) {
        Alert a = get(org, id);
        a.markRead(Instant.now(clock));
        return a;
    }

    public List<UUID> organizationIds() {
        return jdbc.sql("SELECT id FROM organizations").query(UUID.class).list();
    }
}
