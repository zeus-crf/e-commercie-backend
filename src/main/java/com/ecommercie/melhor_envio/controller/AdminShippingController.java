package com.ecommercie.melhor_envio.controller;

import com.ecommercie.config.OpenApiConfig;
import com.ecommercie.melhor_envio.service.ShippingService;
import com.ecommercie.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Frete do Administrador", description = "Onde o Administrador gera etiquetas de envio")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class AdminShippingController {

    private final ShippingService shippingService;

    @Operation(summary = "Gera a etiqueta no Melhor Envio e marca o pedido como enviado")
    @PostMapping("/{id}/label")
    public ResponseEntity<ApiResponse<Void>> gerarEtiqueta(@PathVariable String id) {
        shippingService.gerarEtiqueta(id);
        return ResponseEntity.ok(ApiResponse.ok("Etiqueta gerada e pedido marcado como enviado", null));
    }
}
