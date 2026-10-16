package com.nexusai.catalog;

import com.nexusai.catalog.CatalogQueryService.*;
import com.nexusai.common.PageResponse;
import com.nexusai.security.OrgContext;
import com.nexusai.analytics.AnalyticsModels.LowStockItem;
import com.nexusai.analytics.AnalyticsService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogQueryService catalog;
    private final AnalyticsService analytics;

    public CatalogController(CatalogQueryService catalog, AnalyticsService analytics) {
        this.catalog = catalog;
        this.analytics = analytics;
    }

    @GetMapping("/stores")
    public List<StoreDto> stores(OrgContext ctx) {
        return catalog.stores(ctx.organizationId());
    }

    @GetMapping("/categories")
    public List<String> categories(OrgContext ctx) {
        return catalog.categories(ctx.organizationId());
    }

    @GetMapping("/products")
    public PageResponse<ProductRow> products(OrgContext ctx,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.products(ctx.organizationId(), search, category, Math.max(page, 0), pageSize(size));
    }

    @GetMapping("/products/{id}")
    public ProductDetail product(OrgContext ctx, @PathVariable UUID id) {
        return catalog.product(ctx.organizationId(), id);
    }

    @GetMapping("/inventory")
    public PageResponse<InventoryRow> inventory(OrgContext ctx,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean lowOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.inventory(ctx.organizationId(), storeId, search, lowOnly, Math.max(page, 0), pageSize(size));
    }

    @GetMapping("/inventory/movements")
    public PageResponse<MovementRow> movements(OrgContext ctx,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.movements(ctx.organizationId(), storeId, productId, type, Math.max(page, 0), pageSize(size));
    }

    @GetMapping("/inventory/low-stock")
    public List<LowStockItem> lowStock(OrgContext ctx, @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "20") int limit) {
        return analytics.lowStock(ctx.organizationId(), storeId, limit);
    }

    static int pageSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
