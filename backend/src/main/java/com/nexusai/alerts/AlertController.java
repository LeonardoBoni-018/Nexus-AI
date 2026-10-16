package com.nexusai.alerts;

import com.nexusai.common.PageResponse;
import com.nexusai.identity.Role;
import com.nexusai.security.OrgContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    public record AlertDto(UUID id, Alert.Type type, Alert.Severity severity, UUID storeId, UUID productId,
            String title, String message, boolean read, Instant createdAt) {
        public static AlertDto of(Alert a) {
            return new AlertDto(a.getId(), a.getType(), a.getSeverity(), a.getStoreId(), a.getProductId(),
                    a.getTitle(), a.getMessage(), a.getReadAt() != null, a.getCreatedAt());
        }
    }

    private final AlertService alerts;

    public AlertController(AlertService alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    public PageResponse<AlertDto> list(OrgContext ctx, @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(alerts.active(ctx.organizationId(), unreadOnly,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))).map(AlertDto::of));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(OrgContext ctx) {
        return Map.of("count", alerts.unreadCount(ctx.organizationId()));
    }

    @GetMapping("/{id}")
    public AlertDto get(OrgContext ctx, @PathVariable UUID id) {
        return AlertDto.of(alerts.get(ctx.organizationId(), id));
    }

    @PatchMapping("/{id}/read")
    public AlertDto read(OrgContext ctx, @PathVariable UUID id) {
        return AlertDto.of(alerts.markRead(ctx.organizationId(), id));
    }

    /** Reavalia as regras agora (tambem roda periodicamente). */
    @PostMapping("/evaluate")
    public Map<String, Integer> evaluate(OrgContext ctx) {
        ctx.require(Role.ANALYST);
        return Map.of("changes", alerts.evaluate(ctx.organizationId()));
    }
}
