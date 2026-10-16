package com.nexusai.catalog;

import com.nexusai.catalog.CatalogRepositories.BalanceRepository;
import com.nexusai.catalog.CatalogRepositories.MovementRepository;
import com.nexusai.catalog.CatalogRepositories.ProductRepository;
import com.nexusai.catalog.CatalogRepositories.StoreRepository;
import com.nexusai.common.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unico ponto que altera saldos de estoque. Cada alteracao grava o movimento correspondente na mesma
 * transacao, com lock pessimista no saldo e chave de idempotencia opcional para evitar duplicidade em retries.
 */
@Service
public class InventoryService {

    private final BalanceRepository balances;
    private final MovementRepository movements;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final Clock clock;

    public InventoryService(BalanceRepository balances, MovementRepository movements, StoreRepository stores,
            ProductRepository products, Clock clock) {
        this.balances = balances;
        this.movements = movements;
        this.stores = stores;
        this.products = products;
        this.clock = clock;
    }

    /**
     * Aplica uma variacao de estoque.
     *
     * @param idempotencyKey se ja existir movimento com a chave, nada e aplicado e o movimento existente e devolvido
     * @throws ApiException 409 se a variacao deixaria o saldo negativo
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryMovement apply(UUID organizationId, UUID storeId, UUID productId, InventoryMovement.Type type,
            int delta, String referenceType, UUID referenceId, String idempotencyKey) {
        if (delta == 0) {
            throw ApiException.badRequest("Variacao de estoque nao pode ser zero");
        }
        if (idempotencyKey != null) {
            Optional<InventoryMovement> existing = movements.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        // Valida escopo: loja e produto devem pertencer a organizacao.
        stores.findByIdAndOrganizationId(storeId, organizationId).orElseThrow(() -> ApiException.notFound("Loja"));
        products.findByIdAndOrganizationId(productId, organizationId).orElseThrow(() -> ApiException.notFound("Produto"));

        Instant now = Instant.now(clock);
        InventoryBalance balance = balances.lockByStoreAndProduct(storeId, productId).orElse(null);
        if (balance == null) {
            if (delta < 0) {
                throw ApiException.conflict("INSUFFICIENT_STOCK", "Estoque insuficiente");
            }
            balance = new InventoryBalance(organizationId, storeId, productId, 0, now);
        }
        try {
            balance.apply(delta, now);
        } catch (IllegalStateException e) {
            throw ApiException.conflict("INSUFFICIENT_STOCK", e.getMessage());
        }
        balances.save(balance);
        return movements.save(new InventoryMovement(organizationId, storeId, productId, type, delta,
                balance.getQuantity(), referenceType, referenceId, idempotencyKey, now));
    }
}
