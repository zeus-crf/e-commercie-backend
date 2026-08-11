package com.ecommercie.pedido.controller;

import com.ecommercie.pedido.dtos.OrderResponse;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.service.OrderService;
import com.ecommercie.shared.ApiResponse;
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
public class OrderAdminController {

    private final OrderService orderService;

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> listarTodos(@RequestParam(required = false) StatusOrder status,
                                                                        Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.listarTodos(status, pageable)));
    }

    @GetMapping("/customers/{userId}/orders")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> listarPorCliente(@PathVariable String userId,
                                                                             @RequestParam(required = false) StatusOrder status,
                                                                             Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.listarPorCliente(userId, status, pageable)));
    }

    @PatchMapping("/orders/{id}/separar")
    public ResponseEntity<ApiResponse<OrderResponse>> separar(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.separar(id)));
    }

    @PatchMapping("/orders/{id}/enviar")
    public ResponseEntity<ApiResponse<OrderResponse>> enviar(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.enviar(id)));
    }

    @PatchMapping("/orders/{id}/entregar")
    public ResponseEntity<ApiResponse<OrderResponse>> entregar(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.entregar(id)));
    }
}
