package com.ecommercie.pedido.controller;

import com.ecommercie.config.OpenApiConfig;
import com.ecommercie.pedido.dtos.CheckoutRequest;
import com.ecommercie.pedido.dtos.OrderResponse;
import com.ecommercie.pedido.service.OrderService;
import com.ecommercie.security.models.User;
import com.ecommercie.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Pedidos", description = "Pedidos do cliente autenticado")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Finaliza a compra, gerando um pedido a partir do carrinho")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(@AuthenticationPrincipal UserDetails userDetails,
                                                               @RequestBody @Valid CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pedido realizado com sucesso",
                        orderService.checkout((User) userDetails, request)));
    }

    @Operation(summary = "Lista os pedidos do cliente (paginado)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> listarMeusPedidos(@AuthenticationPrincipal UserDetails userDetails,
                                                                              Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.listarMeusPedidos((User) userDetails, pageable)));
    }

    @Operation(summary = "Exibe um pedido do cliente")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> meuPedido(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.meuPedido((User) userDetails, orderId)));
    }

    @Operation(summary = "Cancela um pedido do cliente")
    @PatchMapping("/{orderId}/cancelar")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelar(@AuthenticationPrincipal UserDetails userDetails,
                                                               @PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.cancelar((User) userDetails, orderId)));
    }
}
