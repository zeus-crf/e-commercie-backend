package com.ecommercie.melhor_envio.controller;

import com.ecommercie.melhor_envio.dto.MeTrackingEvent;
import com.ecommercie.melhor_envio.service.ShipmentTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/melhorenvio")
@RequiredArgsConstructor
public class MelhorEnvioWebhookController {

    private final ShipmentTrackingService trackingService;

    @GetMapping
    public ResponseEntity<Void> validate() {
        return ResponseEntity.ok().build();
    }

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
