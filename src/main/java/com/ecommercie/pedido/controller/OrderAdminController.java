package com.ecommercie.pedido.controller;

import com.ecommercie.pedido.dtos.OrderResponse;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.service.OrderService;
import com.ecommercie.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Administração de pedidos — sob /api/v1/admin/** (ROLE_ADMIN).
 * Transições de fulfillment (separar/enviar/entregar) e histórico por cliente.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Pedidos do Administrador", description = "Onde o Administrador gerencia os pedidos e suas transições de status")
public class OrderAdminController {

    private final OrderService orderService;

    @Operation(summary = "Lista todos os pedidos, com filtro opcional por status")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> listarTodos(@RequestParam(required = false) StatusOrder status,
                                                                        Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.listarTodos(status, pageable)));
    }

    @Operation(summary = "Lista os pedidos de um cliente, com filtro opcional por status")
    @GetMapping("/customers/{userId}/orders")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> listarPorCliente(@PathVariable String userId,
                                                                             @RequestParam(required = false) StatusOrder status,
                                                                             Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.listarPorCliente(userId, status, pageable)));
    }

    @Operation(summary = "Marca o pedido como em separação")
    @PatchMapping("/orders/{id}/separar")
    public ResponseEntity<ApiResponse<OrderResponse>> separar(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.separar(id)));
    }

    @Operation(summary = "Marca o pedido como enviado")
    @PatchMapping("/orders/{id}/enviar")
    public ResponseEntity<ApiResponse<OrderResponse>> enviar(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.enviar(id)));
    }

    @Operation(summary = "Marca o pedido como entregue")
    @PatchMapping("/orders/{id}/entregar")
    public ResponseEntity<ApiResponse<OrderResponse>> entregar(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.entregar(id)));
    }
}
