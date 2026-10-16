package com.nexusai.sales;

import com.nexusai.analytics.AnalyticsModels.PeriodComparison;
import com.nexusai.analytics.AnalyticsModels.SalesMetrics;
import com.nexusai.analytics.AnalyticsService;
import com.nexusai.analytics.AnalyticsService.Filters;
import com.nexusai.common.PageResponse;
import com.nexusai.common.PeriodResolver;
import com.nexusai.sales.SalesQueryService.SaleDetail;
import com.nexusai.sales.SalesQueryService.SaleRow;
import com.nexusai.security.OrgContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SalesQueryService sales;
    private final AnalyticsService analytics;
    private final PeriodResolver periods;

    public SalesController(SalesQueryService sales, AnalyticsService analytics, PeriodResolver periods) {
        this.sales = sales;
        this.analytics = analytics;
        this.periods = periods;
    }

    @GetMapping
    public PageResponse<SaleRow> list(OrgContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sales.list(ctx.organizationId(), periods.resolve(from, to), storeId, Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/{id}")
    public SaleDetail detail(OrgContext ctx, @PathVariable UUID id) {
        return sales.detail(ctx.organizationId(), id);
    }

    @GetMapping("/summary")
    public SalesMetrics summary(OrgContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID productId) {
        return analytics.salesMetrics(ctx.organizationId(), periods.resolve(from, to),
                new Filters(storeId, category, productId));
    }

    @GetMapping("/comparison")
    public PeriodComparison comparison(OrgContext ctx,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID productId) {
        return analytics.compare(ctx.organizationId(), periods.resolve(from, to),
                new Filters(storeId, category, productId));
    }
}
