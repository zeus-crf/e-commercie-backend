package com.ecommercie.estoque.repository;

import com.ecommercie.estoque.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {

    @Query("SELECT i FROM InventoryItem i WHERE i.variant.id = :variantId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryItem> lockForUpdate(@Param("variantId") String variantId);

    Optional<InventoryItem> findByVariantId(String variantId);

    List<InventoryItem> findByVariantProductId(String productId);
    
}
