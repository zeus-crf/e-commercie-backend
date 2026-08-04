package com.ecommercie.estoque.repository;

import com.ecommercie.estoque.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {

    @Query("SELECT i FROM InventoryItem i WHERE i.product.id = :productId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryItem> lockForUpdate(@Param("productId") String productId);

    Optional<InventoryItem> findByProductId(String productId);
}
