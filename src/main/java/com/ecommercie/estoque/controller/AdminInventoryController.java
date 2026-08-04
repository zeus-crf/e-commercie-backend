package com.ecommercie.estoque.controller;

import com.ecommercie.estoque.dto.InventoryItemRequest;
import com.ecommercie.estoque.service.InventoryService;
import com.ecommercie.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;

    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> ajustarEstoque(@PathVariable String productId, @Valid @RequestBody InventoryItemRequest request) {
        inventoryService.ajustarEstoque(productId, request.disponivel());
        return ResponseEntity.ok(ApiResponse.ok("Estoque atualizado", null));
    }
}
