package com.nexusai.analytics;

import com.nexusai.actions.ActionQueryService;
import com.nexusai.actions.ActionQueryService.ActionSummary;
import com.nexusai.alerts.AlertController.AlertDto;
import com.nexusai.alerts.AlertService;
import com.nexusai.analytics.AnalyticsModels.PeriodComparison;
import com.nexusai.analytics.AnalyticsModels.SeriesPoint;
import com.nexusai.analytics.AnalyticsModels.StaleProduct;
import com.nexusai.analytics.AnalyticsModels.TopProduct;
import com.nexusai.analytics.AnalyticsService.Filters;
import com.nexusai.common.PeriodResolver;
import com.nexusai.security.OrgContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    public record DashboardSummary(PeriodComparison comparison, int lowStockCount, long unreadAlerts) {}

    private final AnalyticsService analytics;
    private final AlertService alerts;
    private final ActionQueryService actions;
    private final PeriodResolver periods;

    public DashboardController(AnalyticsService analytics, AlertService alerts, ActionQueryService actions,
            PeriodResolver periods) {
        this.analytics = analytics;
        this.alerts = alerts;
        this.actions = actions;
        this.periods = periods;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(OrgContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID storeId, @RequestParam(required = false) String category) {
        var cmp = analytics.compare(ctx.organizationId(), periods.resolve(from, to), new Filters(storeId, category, null));
        int low = analytics.lowStock(ctx.organizationId(), storeId, 100).size();
        return new DashboardSummary(cmp, low, alerts.unreadCount(ctx.organizationId()));
    }

    @GetMapping("/sales-series")
    public List<SeriesPoint> series(OrgContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID storeId, @RequestParam(required = false) String category) {
        return analytics.salesSeries(ctx.organizationId(), periods.resolve(from, to), new Filters(storeId, category, null));
    }

    @GetMapping("/top-products")
    public List<TopProduct> top(OrgContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID storeId, @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "units") String orderBy, @RequestParam(defaultValue = "5") int limit) {
        return analytics.topProducts(ctx.organizationId(), periods.resolve(from, to),
                new Filters(storeId, category, null), limit, orderBy);
    }

    @GetMapping("/stale-products")
    public List<StaleProduct> stale(OrgContext ctx, @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "30") int days, @RequestParam(defaultValue = "5") int limit) {
        int d = Math.min(Math.max(days, 1), 365);
        return analytics.staleProducts(ctx.organizationId(), storeId, periods.today().minusDays(d), limit);
    }

    @GetMapping("/alerts")
    public List<AlertDto> alerts(OrgContext ctx) {
        return alerts.active(ctx.organizationId(), false, PageRequest.of(0, 5)).map(AlertDto::of).getContent();
    }

    @GetMapping("/recent-actions")
    public List<ActionSummary> recentActions(OrgContext ctx) {
        return actions.list(ctx.organizationId(), null, null, 0, 5).items();
    }
}
