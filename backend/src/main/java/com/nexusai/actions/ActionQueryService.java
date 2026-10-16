package com.nexusai.actions;

import com.nexusai.analytics.AnalyticsService;
import com.nexusai.common.PageResponse;
import com.nexusai.common.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ActionQueryService {

    public record ActionSummary(UUID id, String type, String status, UUID storeId, String storeName,
            String createdByName, boolean createdViaAi, long itemCount, long totalUnits, Instant createdAt,
            Instant updatedAt) {}

    public record ItemDto(UUID productId, String sku, String productName, int currentStock,
            BigDecimal averageDailySales, BigDecimal coverageDays, int suggestedQuantity, int approvedQuantity) {}

    public record ApprovalDto(String userName, String decision, String reason, Instant createdAt) {}

    public record ActionDetail(UUID id, String type, String status, UUID storeId, String storeName,
            String justification, UUID createdBy, String createdByName, boolean createdViaAi, UUID conversationId,
            Instant createdAt, Instant updatedAt, Instant executedAt, String failureReason, List<ItemDto> items,
            List<ApprovalDto> approvals, List<String> nextStatuses) {}

    private final JdbcClient jdbc;
    private final AnalyticsService analytics;

    public ActionQueryService(JdbcClient jdbc, AnalyticsService analytics) {
        this.jdbc = jdbc;
        this.analytics = analytics;
    }

    public PageResponse<ActionSummary> list(UUID org, String status, UUID storeId, int page, int size) {
        analytics.assertStore(org, storeId);
        Map<String, Object> params = new HashMap<>();
        params.put("org", org);
        StringBuilder where = new StringBuilder(" WHERE a.organization_id = :org");
        if (status != null && !status.isBlank()) {
            try {
                params.put("status", ProposedAction.Status.valueOf(status.toUpperCase()).name());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Status invalido");
            }
            where.append(" AND a.status = :status");
        }
        if (storeId != null) {
            where.append(" AND a.store_id = :store");
            params.put("store", storeId);
        }
        long total = jdbc.sql("SELECT COUNT(*) FROM proposed_actions a" + where).params(params).query(Long.class).single();
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<ActionSummary> rows = jdbc.sql("""
                SELECT a.id, a.type, a.status, a.store_id, st.name AS store_name, u.name AS user_name,
                       a.created_via_ai, a.created_at, a.updated_at,
                       (SELECT COUNT(*) FROM action_items i WHERE i.action_id = a.id) AS item_count,
                       (SELECT COALESCE(SUM(i.approved_quantity), 0) FROM action_items i WHERE i.action_id = a.id) AS units
                FROM proposed_actions a
                JOIN stores st ON st.id = a.store_id
                JOIN users u ON u.id = a.created_by
                """ + where + " ORDER BY a.created_at DESC, a.id LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> new ActionSummary(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getString("status"), rs.getObject("store_id", UUID.class), rs.getString("store_name"),
                        rs.getString("user_name"), rs.getBoolean("created_via_ai"), rs.getLong("item_count"),
                        rs.getLong("units"), ts(rs, "created_at"), ts(rs, "updated_at")))
                .list();
        return PageResponse.of(rows, page, size, total);
    }

    public ActionDetail detail(UUID org, UUID id) {
        ActionDetail head = jdbc.sql("""
                SELECT a.id, a.type, a.status, a.store_id, st.name AS store_name, a.justification, a.created_by,
                       u.name AS user_name, a.created_via_ai, a.conversation_id, a.created_at, a.updated_at,
                       a.executed_at, a.failure_reason
                FROM proposed_actions a
                JOIN stores st ON st.id = a.store_id
                JOIN users u ON u.id = a.created_by
                WHERE a.id = :id AND a.organization_id = :org
                """).param("id", id).param("org", org)
                .query((rs, n) -> new ActionDetail(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getString("status"), rs.getObject("store_id", UUID.class), rs.getString("store_name"),
                        rs.getString("justification"), rs.getObject("created_by", UUID.class),
                        rs.getString("user_name"), rs.getBoolean("created_via_ai"),
                        rs.getObject("conversation_id", UUID.class), ts(rs, "created_at"), ts(rs, "updated_at"),
                        ts(rs, "executed_at"), rs.getString("failure_reason"), null, null, null))
                .optional().orElseThrow(() -> ApiException.notFound("Proposta"));
        List<ItemDto> items = jdbc.sql("""
                SELECT p.id, p.sku, p.name, i.current_stock, i.avg_daily_sales, i.coverage_days,
                       i.suggested_quantity, i.approved_quantity
                FROM action_items i JOIN products p ON p.id = i.product_id
                WHERE i.action_id = :id ORDER BY p.name
                """).param("id", id)
                .query((rs, n) -> new ItemDto(rs.getObject("id", UUID.class), rs.getString("sku"),
                        rs.getString("name"), rs.getInt("current_stock"), rs.getBigDecimal("avg_daily_sales"),
                        rs.getBigDecimal("coverage_days"), rs.getInt("suggested_quantity"),
                        rs.getInt("approved_quantity")))
                .list();
        List<ApprovalDto> approvals = jdbc.sql("""
                SELECT u.name, ap.decision, ap.reason, ap.created_at
                FROM action_approvals ap JOIN users u ON u.id = ap.user_id
                WHERE ap.action_id = :id ORDER BY ap.created_at
                """).param("id", id)
                .query((rs, n) -> new ApprovalDto(rs.getString("name"), rs.getString("decision"),
                        rs.getString("reason"), ts(rs, "created_at")))
                .list();
        ProposedAction.Status current = ProposedAction.Status.valueOf(head.status());
        List<String> next = java.util.Arrays.stream(ProposedAction.Status.values())
                .filter(s -> canTransition(current, s)).map(Enum::name).toList();
        return new ActionDetail(head.id(), head.type(), head.status(), head.storeId(), head.storeName(),
                head.justification(), head.createdBy(), head.createdByName(), head.createdViaAi(),
                head.conversationId(), head.createdAt(), head.updatedAt(), head.executedAt(), head.failureReason(),
                items, approvals, next);
    }

    private static boolean canTransition(ProposedAction.Status from, ProposedAction.Status to) {
        return switch (from) {
            case DRAFT -> to == ProposedAction.Status.PENDING_APPROVAL || to == ProposedAction.Status.CANCELLED;
            case PENDING_APPROVAL -> to == ProposedAction.Status.APPROVED || to == ProposedAction.Status.REJECTED
                    || to == ProposedAction.Status.CANCELLED;
            case APPROVED -> to == ProposedAction.Status.EXECUTED || to == ProposedAction.Status.CANCELLED;
            default -> false;
        };
    }

    private static Instant ts(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
        return o == null ? null : o.toInstant();
    }
}
