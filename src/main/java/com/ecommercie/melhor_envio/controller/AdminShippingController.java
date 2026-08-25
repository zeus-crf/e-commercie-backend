package com.ecommercie.melhor_envio.controller;

import com.ecommercie.melhor_envio.service.ShippingService;
import com.ecommercie.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminShippingController {

    private final ShippingService shippingService;

    @PostMapping("/{id}/label")
    public ResponseEntity<ApiResponse<Void>> gerarEtiqueta(@PathVariable String id) {
        shippingService.gerarEtiqueta(id);
        return ResponseEntity.ok(ApiResponse.ok("Etiqueta gerada e pedido marcado como enviado", null));
    }
}
