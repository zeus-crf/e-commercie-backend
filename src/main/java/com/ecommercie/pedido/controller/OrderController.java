package com.ecommercie.pedido.controller;

import com.ecommercie.pedido.dtos.CheckoutRequest;
import com.ecommercie.pedido.dtos.OrderResponse;
import com.ecommercie.pedido.service.OrderService;
import com.ecommercie.security.models.User;
import com.ecommercie.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Pedidos do cliente. Autenticado; o dono vem do token (@AuthenticationPrincipal).
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(@AuthenticationPrincipal UserDetails userDetails,
                                                               @RequestBody @Valid CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pedido realizado com sucesso",
                        orderService.checkout((User) userDetails, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> listarMeusPedidos(@AuthenticationPrincipal UserDetails userDetails,
                                                                              Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.listarMeusPedidos((User) userDetails, pageable)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> meuPedido(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.meuPedido((User) userDetails, orderId)));
    }

    @PatchMapping("/{orderId}/cancelar")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelar(@AuthenticationPrincipal UserDetails userDetails,
                                                               @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.cancelar((User) userDetails, orderId)));
    }
}
