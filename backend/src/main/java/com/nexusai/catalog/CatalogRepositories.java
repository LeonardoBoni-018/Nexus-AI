package com.nexusai.catalog;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public final class CatalogRepositories {

    private CatalogRepositories() {}

    public interface StoreRepository extends JpaRepository<Store, UUID> {
        List<Store> findByOrganizationIdOrderByName(UUID organizationId);

        Optional<Store> findByIdAndOrganizationId(UUID id, UUID organizationId);
    }

    public interface ProductRepository extends JpaRepository<Product, UUID> {
        Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId);

        List<Product> findByIdInAndOrganizationId(List<UUID> ids, UUID organizationId);
    }

    public interface BalanceRepository extends JpaRepository<InventoryBalance, UUID> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select b from InventoryBalance b where b.storeId = :storeId and b.productId = :productId")
        Optional<InventoryBalance> lockByStoreAndProduct(UUID storeId, UUID productId);
    }

    public interface MovementRepository extends JpaRepository<InventoryMovement, UUID> {
        Optional<InventoryMovement> findByIdempotencyKey(String idempotencyKey);
    }
}
