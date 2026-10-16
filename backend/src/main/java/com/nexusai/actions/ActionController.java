package com.nexusai.actions;

import com.nexusai.actions.ActionQueryService.ActionDetail;
import com.nexusai.actions.ActionQueryService.ActionSummary;
import com.nexusai.common.PageResponse;
import com.nexusai.security.OrgContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/actions")
public class ActionController {

    public record CreateReplenishmentRequest(@NotNull UUID storeId, @Size(max = 50) List<UUID> productIds,
            @Size(max = 1900) String justification) {}

    public record UpdateRequest(@Size(max = 1900) String justification, Map<UUID, Integer> quantities) {}

    public record DecisionRequest(@Size(max = 900) String reason) {}

    private final ActionService service;
    private final ActionQueryService queries;

    public ActionController(ActionService service, ActionQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    public PageResponse<ActionSummary> list(OrgContext ctx, @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID storeId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.list(ctx.organizationId(), status, storeId, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/{id}")
    public ActionDetail get(OrgContext ctx, @PathVariable UUID id) {
        return queries.detail(ctx.organizationId(), id);
    }

    @PostMapping("/replenishment")
    @ResponseStatus(HttpStatus.CREATED)
    public ActionDetail create(OrgContext ctx, @Valid @RequestBody CreateReplenishmentRequest body) {
        Set<UUID> ids = body.productIds() == null ? Set.of() : body.productIds().stream().collect(Collectors.toSet());
        UUID id = service.createReplenishment(ctx, body.storeId(), ids, body.justification(), false, null);
        return queries.detail(ctx.organizationId(), id);
    }

    @PatchMapping("/{id}")
    public ActionDetail update(OrgContext ctx, @PathVariable UUID id, @Valid @RequestBody UpdateRequest body) {
        service.update(ctx, id, body.justification(), body.quantities());
        return queries.detail(ctx.organizationId(), id);
    }

    @PostMapping("/{id}/submit")
    public ActionDetail submit(OrgContext ctx, @PathVariable UUID id) {
        service.submit(ctx, id);
        return queries.detail(ctx.organizationId(), id);
    }

    @PostMapping("/{id}/approve")
    public ActionDetail approve(OrgContext ctx, @PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest body) {
        service.approve(ctx, id, body == null ? null : body.reason());
        return queries.detail(ctx.organizationId(), id);
    }

    @PostMapping("/{id}/reject")
    public ActionDetail reject(OrgContext ctx, @PathVariable UUID id, @Valid @RequestBody DecisionRequest body) {
        service.reject(ctx, id, body.reason());
        return queries.detail(ctx.organizationId(), id);
    }

    @PostMapping("/{id}/cancel")
    public ActionDetail cancel(OrgContext ctx, @PathVariable UUID id) {
        service.cancel(ctx, id);
        return queries.detail(ctx.organizationId(), id);
    }

    @PostMapping("/{id}/execute")
    public ActionDetail execute(OrgContext ctx, @PathVariable UUID id) {
        service.execute(ctx, id);
        return queries.detail(ctx.organizationId(), id);
    }
}
