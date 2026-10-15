package com.orderflow.inventoryservice.repository;

import com.orderflow.inventoryservice.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    /**
     * Row-level lock for stock mutations - concurrent orders for the same product serialize here
     * rather than racing on the read-check-write of available quantity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId")
    Optional<InventoryItem> findForUpdate(@Param("productId") UUID productId);
}
