package com.ecommercie.catalogo.controller;

import com.ecommercie.catalogo.dtos.CategoryRequest;
import com.ecommercie.catalogo.dtos.CategoryResponse;
import com.ecommercie.catalogo.dtos.ProductRequest;
import com.ecommercie.catalogo.dtos.ProductResponse;
import com.ecommercie.catalogo.service.CatalogService;
import com.ecommercie.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Administração do catálogo — escrita. Sob {@code /api/v1/admin/**},
 * que exige ROLE_ADMIN no SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalogo do Administrador", description = "Onde o Administrador para realizar operações de controle do catalogo")
public class AdminCatalogController {

    private final CatalogService catalogService;

    // ---------- produtos ----------

    @Operation(summary = "Criação de um novo produto")
    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Produto criado com sucesso", catalogService.createProduct(request)));
    }

    @Operation(summary = "Edição de um produto já existente")
    // sem @Valid: edição é parcial (o service faz null-check campo a campo)
    @PutMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> editProduct(@PathVariable String productId,
                                                                    @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Produto editado com sucesso",
                catalogService.editProduct(productId, request)));
    }

    @Operation(summary = "Remove um produto")
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String productId) {
        catalogService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    // ---------- imagens ----------

    @Operation(summary = "Adiciona uma imagem ao produto (multipart, campo \"imagem\")")
    @PostMapping(value = "/products/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductResponse>> adicionarImagem(@PathVariable String productId,
                                                                        @RequestParam("imagem") MultipartFile imagem) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Imagem adicionada com sucesso",
                        catalogService.adicionarImagem(productId, imagem)));
    }

    @Operation(summary = "Remove uma imagem do produto pela URL")
    @DeleteMapping("/products/{productId}/images")
    public ResponseEntity<ApiResponse<ProductResponse>> deletarImagem(@PathVariable String productId,
                                                                      @RequestParam String url) {
        return ResponseEntity.ok(ApiResponse.ok("Imagem removida com sucesso",
                catalogService.deletarImagem(productId, url)));
    }

    // ---------- categorias ----------

    @Operation(summary = "Criação de uma nova categoria")
    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Categoria criada com sucesso", catalogService.createCategory(request)));
    }

    @Operation(summary = "Edição de uma categoria já existente")
    // sem @Valid: edição é parcial
    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> editCategory(@PathVariable String categoryId,
                                                                      @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Categoria editada com sucesso",
                catalogService.editCategory(categoryId, request)));
    }

    @Operation(summary = "Remove uma categoria")
    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable String categoryId) {
        catalogService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}
