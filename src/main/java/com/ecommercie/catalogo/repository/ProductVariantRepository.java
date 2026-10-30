package com.ecommercie.catalogo.repository;

import com.ecommercie.catalogo.models.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {

}