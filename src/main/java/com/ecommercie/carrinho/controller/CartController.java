package com.ecommercie.carrinho.controller;

import com.ecommercie.carrinho.dto.CartItemRequest;
import com.ecommercie.carrinho.dto.CartResponse;
import com.ecommercie.carrinho.service.CartService;
import com.ecommercie.security.models.User;
import com.ecommercie.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getOrCreate(@AuthenticationPrincipal UserDetails userDetails) {
        var usuario = (User) userDetails;
        return ResponseEntity.ok(ApiResponse.ok(cartService.getOrCreate(usuario)));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CartItemRequest request) {
        var usuario = (User) userDetails;
        return ResponseEntity.ok(ApiResponse.ok(cartService.addItem(usuario, request)));
    }

    @PatchMapping("/items/edit")
    public ResponseEntity<ApiResponse<CartResponse>> editItem(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CartItemRequest request){
        var usuario = (User) userDetails;
        return ResponseEntity.ok(ApiResponse.ok(cartService.editItem(usuario, request)));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String itemId){
        var usuario = (User) userDetails;
        return ResponseEntity.ok(ApiResponse.ok(cartService.removeItem(usuario, itemId )));
    }
}
