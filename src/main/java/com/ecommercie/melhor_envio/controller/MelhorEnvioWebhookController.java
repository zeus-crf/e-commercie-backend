package com.ecommercie.melhor_envio.controller;

import com.ecommercie.melhor_envio.dto.MeTrackingEvent;
import com.ecommercie.melhor_envio.service.ShipmentTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/melhorenvio")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Callbacks chamados por provedores externos")
public class MelhorEnvioWebhookController {

    private final ShipmentTrackingService trackingService;

    @Operation(summary = "Validação do webhook pelo Melhor Envio",
            description = "Chamado pelo provedor externo. Responde 200 OK puro, sem o envelope ApiResponse")
    @GetMapping
    public ResponseEntity<Void> validate() {
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Recebe eventos de rastreio do Melhor Envio",
            description = "Chamado pelo provedor externo. Responde 200 OK puro, sem o envelope ApiResponse")
    @PostMapping
    public ResponseEntity<Void> tracking(@RequestBody MeTrackingEvent event) {
        try {
            trackingService.processar(event);
        } catch (Exception ex) {
            log.error("Erro ao processar tracking do ME: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.ok().build();
    }
}
