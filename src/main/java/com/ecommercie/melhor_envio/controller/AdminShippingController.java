package com.ecommercie.melhor_envio.controller;

import com.ecommercie.config.OpenApiConfig;
import com.ecommercie.melhor_envio.dto.ImpressaoEtiquetaResponse;
import com.ecommercie.melhor_envio.service.ShippingService;
import com.ecommercie.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Frete do Administrador", description = "Onde o Administrador gera e imprime etiquetas de envio")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class AdminShippingController {

    private final ShippingService shippingService;

    @Operation(summary = "Paga e gera a etiqueta no Melhor Envio (o pedido vira ENVIADO quando o ME avisar a postagem)")
    @PostMapping("/{id}/label")
    public ResponseEntity<ApiResponse<Void>> gerarEtiqueta(@PathVariable String id) {
        shippingService.gerarEtiqueta(id);
        return ResponseEntity.ok(ApiResponse.ok("Etiqueta paga e gerada no Melhor Envio. Imprima com GET /api/v1/admin/orders/"
                + id + "/label/print; o pedido vira enviado na postagem", null));
    }

    @Operation(summary = "Link para imprimir a etiqueta já gerada (abre logado no Melhor Envio)")
    @GetMapping("/{id}/label/print")
    public ResponseEntity<ApiResponse<ImpressaoEtiquetaResponse>> gerarImpressao(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok("Impressão gerada com sucesso ", new ImpressaoEtiquetaResponse(shippingService.linkImpressao(id))));
    }
}
