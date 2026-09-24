package com.ecommercie.carrinho.controller;

import com.ecommercie.carrinho.dto.CartItemRequest;
import com.ecommercie.carrinho.dto.CartResponse;
import com.ecommercie.carrinho.service.CartService;
import com.ecommercie.security.models.User;
import com.ecommercie.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Carrinho", description = "Operações do carrinho do cliente")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Crie ou exibe um carrinho de um cliente")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getOrCreate(@AuthenticationPrincipal UserDetails userDetails) {
        var usuario = (User) userDetails;
        return ResponseEntity.ok(ApiResponse.ok(cartService.getOrCreate(usuario)));
    }

    @Operation(summary = "Adiciona um item no carrinho")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CartItemRequest request) {
        var usuario = (User) userDetails;
        return ResponseEntity.ok(ApiResponse.ok(cartService.addItem(usuario, request)));
    }

    @Operation(summary = "Edita um item no carrinho")
    @PatchMapping("/items/edit")
    public ResponseEntity<ApiResponse<CartResponse>> editItem(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CartItemRequest request){
        var usuario = (User) userDetails;
        return ResponseEntity.ok(ApiResponse.ok(cartService.editItem(usuario, request)));
    }

    @Operation(summary = "Remove um item no carrinho")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String itemId){
        var usuario = (User) userDetails;
        return ResponseEntity.ok(ApiResponse.ok(cartService.removeItem(usuario, itemId )));
    }
}
