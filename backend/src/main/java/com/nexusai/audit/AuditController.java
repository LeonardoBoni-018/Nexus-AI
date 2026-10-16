package com.nexusai.audit;

import com.nexusai.common.PageResponse;
import com.nexusai.identity.Role;
import com.nexusai.security.OrgContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {

    public record AuditEventDto(UUID id, UUID userId, String eventType, String entityType, String entityId,
            String details, String requestId, Instant createdAt) {}

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditEventDto> list(OrgContext ctx,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        ctx.require(Role.MANAGER);
        var result = audit.list(ctx.organizationId(), eventType, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.of(result.map(e -> new AuditEventDto(e.getId(), e.getUserId(), e.getEventType(),
                e.getEntityType(), e.getEntityId(), e.getDetails(), e.getRequestId(), e.getCreatedAt())));
    }
}
