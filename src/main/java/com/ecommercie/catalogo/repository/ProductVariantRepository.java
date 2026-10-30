package com.ecommercie.catalogo.repository;

import com.ecommercie.catalogo.models.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {

    boolean existsByProductIdAndTamanhoAndCor(String productId, String tamanho, String cor);

    boolean existsBySku(String sku);

}