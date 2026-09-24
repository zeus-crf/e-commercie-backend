package com.ecommercie.estoque.controller;

import com.ecommercie.estoque.dto.InventoryItemRequest;
import com.ecommercie.estoque.service.InventoryService;
import com.ecommercie.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
@Tag(name = "Estoque do Administrador", description = "Onde o Administrador ajusta o estoque dos produtos")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "Ajusta a quantidade disponível em estoque de um produto")
    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> ajustarEstoque(@PathVariable String productId, @Valid @RequestBody InventoryItemRequest request) {
        inventoryService.ajustarEstoque(productId, request.disponivel());
        return ResponseEntity.ok(ApiResponse.ok("Estoque atualizado", null));
    }
}
