package com.ecommercie.catalogo.controller;

import com.ecommercie.catalogo.dtos.CategoryResponse;
import com.ecommercie.catalogo.dtos.ProductResponse;
import com.ecommercie.catalogo.service.CatalogService;
import com.ecommercie.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo público — somente leitura. Liberado no SecurityConfig
 * ({@code GET /api/v1/catalog/**} = permitAll). Só produtos ativos.
 */
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> listProducts(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.listProducts(pageable)));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.listByIdProduct(productId)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<Page<CategoryResponse>>> listCategories(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.listCategory(pageable)));
    }

    @GetMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable String categoryId) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.listByIdCategory(categoryId)));
    }
}
