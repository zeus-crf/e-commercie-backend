package com.ecommercie.mercado_pago.controller;

import com.ecommercie.mercado_pago.dtos.MercadoPagoNotification;
import com.ecommercie.mercado_pago.service.WebhookService;
import com.ecommercie.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/mercadopago")
    public ResponseEntity<ApiResponse<?>> receberNotificacao(@RequestBody MercadoPagoNotification notification) {
        // MP envia pings de teste e outros tipos (subscriptions, etc.) sem data — ignora
        if (!"payment".equals(notification.type()) || notification.data() == null || notification.data().id() == null) {
            log.info("Webhook MP ignorado: type={} action={}", notification.type(), notification.action());
            return ResponseEntity.ok(ApiResponse.ok("ok", null));
        }
        try {
            webhookService.processarNotificacao(notification);
        } catch (Exception ex) {
            log.error("Erro ao processar webhook MP id={}: {}", notification.data().id(), ex.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.ok("ok", null));
    }
}
