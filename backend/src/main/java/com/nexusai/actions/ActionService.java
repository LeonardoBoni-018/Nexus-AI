package com.nexusai.actions;

import com.nexusai.actions.ActionApproval.Decision;
import com.nexusai.actions.ProposedAction.Status;
import com.nexusai.analytics.AnalyticsService;
import com.nexusai.audit.AuditService;
import com.nexusai.catalog.InventoryMovement;
import com.nexusai.catalog.InventoryService;
import com.nexusai.common.exception.ApiException;
import com.nexusai.identity.Role;
import com.nexusai.security.OrgContext;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ciclo de vida das propostas. Toda mutacao valida papel, escopo de organizacao e transicao de estado no
 * servidor, e gera evento de auditoria na mesma transacao. Aprovacao/rejeicao/execucao exigem usuario
 * humano autenticado (MANAGER+); a IA so consegue criar rascunhos.
 */
@Service
public class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);

    public interface ActionRepository extends JpaRepository<ProposedAction, UUID> {
        Optional<ProposedAction> findByIdAndOrganizationId(UUID id, UUID organizationId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select a from ProposedAction a where a.id = :id and a.organizationId = :org")
        Optional<ProposedAction> lockByIdAndOrganizationId(UUID id, UUID org);
    }

    public interface ItemRepository extends JpaRepository<ActionItem, UUID> {
        List<ActionItem> findByActionId(UUID actionId);
    }

    public interface ApprovalRepository extends JpaRepository<ActionApproval, UUID> {
    }

    private final ActionRepository actions;
    private final ItemRepository items;
    private final ApprovalRepository approvals;
    private final InventoryService inventory;
    private final ReplenishmentPlanner planner;
    private final AnalyticsService analytics;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    public ActionService(ActionRepository actions, ItemRepository items, ApprovalRepository approvals,
            InventoryService inventory, ReplenishmentPlanner planner, AnalyticsService analytics, AuditService audit,
            Clock clock, PlatformTransactionManager txManager) {
        this.actions = actions;
        this.items = items;
        this.approvals = approvals;
        this.inventory = inventory;
        this.planner = planner;
        this.analytics = analytics;
        this.audit = audit;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public UUID createReplenishment(OrgContext ctx, UUID storeId, Set<UUID> productIds, String justification,
            boolean viaAi, UUID conversationId) {
        ctx.require(Role.ANALYST);
        analytics.assertStore(ctx.organizationId(), storeId);
        var lines = planner.plan(ctx.organizationId(), storeId, productIds);
        if (lines.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "NO_REPLENISHMENT_NEEDED",
                    "Nenhum produto precisa de reposicao com os parametros atuais");
        }
        Instant now = Instant.now(clock);
        String text = justification != null && !justification.isBlank() ? justification.trim()
                : "Reposicao calculada pela cobertura de estoque com base nas vendas dos ultimos "
                        + ReplenishmentPlanner.WINDOW_DAYS + " dias.";
        ProposedAction action = actions.save(new ProposedAction(ctx.organizationId(), storeId,
                text.substring(0, Math.min(text.length(), 1900)), ctx.userId(), viaAi, conversationId, now));
        for (var l : lines) {
            items.save(new ActionItem(action.getId(), l.productId(), l.currentStock(), l.averageDailySales(),
                    l.coverageDays(), l.suggestedQuantity()));
        }
        audit.record(ctx.organizationId(), ctx.userId(), "ACTION_CREATED", "ProposedAction", action.getId(),
                (viaAi ? "via IA; " : "") + lines.size() + " itens");
        return action.getId();
    }

    /** Edita justificativa e quantidades aprovadas (0 remove o item). Somente DRAFT/PENDING_APPROVAL. */
    @Transactional
    public void update(OrgContext ctx, UUID id, String justification, Map<UUID, Integer> quantities) {
        ctx.require(Role.ANALYST);
        ProposedAction a = load(ctx, id);
        if (!a.isEditable()) {
            throw ApiException.conflict("NOT_EDITABLE", "Proposta no estado " + a.getStatus() + " nao pode ser editada");
        }
        Instant now = Instant.now(clock);
        Map<UUID, ActionItem> byProduct = new HashMap<>();
        items.findByActionId(id).forEach(i -> byProduct.put(i.getProductId(), i));
        if (quantities != null) {
            for (var e : quantities.entrySet()) {
                ActionItem item = byProduct.get(e.getKey());
                if (item == null) {
                    throw ApiException.badRequest("Produto nao pertence a esta proposta");
                }
                int q = e.getValue() == null ? -1 : e.getValue();
                if (q < 0 || q > 100_000) {
                    throw ApiException.badRequest("Quantidade invalida (0 a 100000)");
                }
                if (q == 0) {
                    items.delete(item);
                    byProduct.remove(e.getKey());
                } else {
                    item.setApprovedQuantity(q);
                }
            }
            if (byProduct.isEmpty()) {
                throw ApiException.badRequest("A proposta precisa ter ao menos um item");
            }
        }
        if (justification != null) {
            a.updateJustification(justification.substring(0, Math.min(justification.length(), 1900)), now);
        }
        audit.record(ctx.organizationId(), ctx.userId(), "ACTION_EDITED", "ProposedAction", id, null);
    }

    @Transactional
    public void submit(OrgContext ctx, UUID id) {
        ctx.require(Role.ANALYST);
        ProposedAction a = load(ctx, id);
        a.transitionTo(Status.PENDING_APPROVAL, Instant.now(clock));
        audit.record(ctx.organizationId(), ctx.userId(), "ACTION_SUBMITTED", "ProposedAction", id, null);
    }

    @Transactional
    public void approve(OrgContext ctx, UUID id, String reason) {
        ctx.require(Role.MANAGER);
        ProposedAction a = load(ctx, id);
        Instant now = Instant.now(clock);
        a.transitionTo(Status.APPROVED, now);
        approvals.save(new ActionApproval(id, ctx.userId(), Decision.APPROVED, reason, now));
        audit.record(ctx.organizationId(), ctx.userId(), "ACTION_APPROVED", "ProposedAction", id, reason);
    }

    @Transactional
    public void reject(OrgContext ctx, UUID id, String reason) {
        ctx.require(Role.MANAGER);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Informe o motivo da rejeicao");
        }
        ProposedAction a = load(ctx, id);
        Instant now = Instant.now(clock);
        a.transitionTo(Status.REJECTED, now);
        approvals.save(new ActionApproval(id, ctx.userId(), Decision.REJECTED, reason, now));
        audit.record(ctx.organizationId(), ctx.userId(), "ACTION_REJECTED", "ProposedAction", id, reason);
    }

    @Transactional
    public void cancel(OrgContext ctx, UUID id) {
        ctx.require(Role.ANALYST);
        ProposedAction a = load(ctx, id);
        if (!a.getCreatedBy().equals(ctx.userId())) {
            ctx.require(Role.MANAGER);
        }
        a.transitionTo(Status.CANCELLED, Instant.now(clock));
        audit.record(ctx.organizationId(), ctx.userId(), "ACTION_CANCELLED", "ProposedAction", id, null);
    }

    /**
     * Executa a proposta aprovada. Idempotente: se ja foi executada, retorna sem reaplicar. O lock pessimista na
     * linha da proposta serializa execucoes concorrentes; cada movimento usa chave de idempotencia propria.
     * Falhas marcam a proposta como FAILED em transacao independente.
     */
    public void execute(OrgContext ctx, UUID id) {
        ctx.require(Role.MANAGER);
        try {
            requiresNew.executeWithoutResult(s -> doExecute(ctx, id));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Falha ao executar acao {}", id, e);
            requiresNew.executeWithoutResult(s -> markFailed(ctx, id, e.getMessage()));
            throw ApiException.conflict("EXECUTION_FAILED", "Falha ao executar a proposta; consulte o historico");
        }
    }

    private void doExecute(OrgContext ctx, UUID id) {
        ProposedAction a = actions.lockByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Proposta"));
        if (a.getStatus() == Status.EXECUTED) {
            return; // idempotente
        }
        Instant now = Instant.now(clock);
        a.transitionTo(Status.EXECUTED, now); // valida APPROVED -> EXECUTED
        int total = 0;
        for (ActionItem item : items.findByActionId(id)) {
            inventory.apply(a.getOrganizationId(), a.getStoreId(), item.getProductId(),
                    InventoryMovement.Type.REPLENISHMENT, item.getApprovedQuantity(), "ProposedAction", id,
                    "action:" + id + ":product:" + item.getProductId());
            total += item.getApprovedQuantity();
        }
        audit.record(ctx.organizationId(), ctx.userId(), "ACTION_EXECUTED", "ProposedAction", id,
                "total de unidades repostas: " + total);
    }

    private void markFailed(OrgContext ctx, UUID id, String reason) {
        actions.lockByIdAndOrganizationId(id, ctx.organizationId()).ifPresent(a -> {
            if (a.getStatus() == Status.APPROVED) {
                a.fail(reason, Instant.now(clock));
                audit.record(ctx.organizationId(), ctx.userId(), "ACTION_FAILED", "ProposedAction", id, reason);
            }
        });
    }

    private ProposedAction load(OrgContext ctx, UUID id) {
        return actions.findByIdAndOrganizationId(id, ctx.organizationId())
                .orElseThrow(() -> ApiException.notFound("Proposta"));
    }
}
