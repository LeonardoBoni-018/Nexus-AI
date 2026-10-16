package com.nexusai.analytics;

import com.nexusai.analytics.AnalyticsModels.CoverageItem;
import com.nexusai.common.PeriodResolver;
import com.nexusai.security.OrgContext;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final PeriodResolver periods;

    public AnalyticsController(AnalyticsService analytics, PeriodResolver periods) {
        this.analytics = analytics;
        this.periods = periods;
    }

    /** Cobertura de estoque em dias (saldo / media diaria de vendas da janela). */
    @GetMapping("/coverage")
    public List<CoverageItem> coverage(OrgContext ctx, @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "28") int windowDays, @RequestParam(defaultValue = "30") int limit) {
        return analytics.coverage(ctx.organizationId(), storeId, null, periods.today(), windowDays, limit);
    }
}
