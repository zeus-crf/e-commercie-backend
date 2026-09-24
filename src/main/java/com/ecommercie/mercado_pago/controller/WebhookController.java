package com.ecommercie.mercado_pago.controller;

import com.ecommercie.mercado_pago.dtos.MercadoPagoNotification;
import com.ecommercie.mercado_pago.service.WebhookService;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Callbacks chamados por provedores externos")
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    @Operation(summary = "Recebe notificações do Mercado Pago",
            description = "Chamado pelo provedor externo. Aceita os formatos webhook (type/data.id) e IPN (topic/id). "
                        + "Responde 200 OK puro, sem o envelope ApiResponse. Responde 500 em falha de comunicação com o MP, para que ele reenvie")
    @PostMapping("/mercadopago")
    public ResponseEntity<Void> receberNotificacao(
            @RequestBody(required = false) MercadoPagoNotification notification,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String topic) {

        if (notification == null) {
            notification = new MercadoPagoNotification(null, null, topic, null, id != null ? new MercadoPagoNotification.Data(id) : null);
        }

        // Normaliza: suporta formato webhook (type/data.id) e IPN (topic/query param id)
        String tipo = notification.tipoNormalizado() != null ? notification.tipoNormalizado() : topic;
        String paymentId = (notification.data() != null && notification.data().id() != null)
                ? notification.data().id()
                : id;

        log.info("Webhook MP recebido: tipo={} paymentId={}", tipo, paymentId);

        if (paymentId == null) {
            log.info("Webhook MP ignorado: tipo={} sem id", tipo);
            return ResponseEntity.ok().build();
        }

        try {
            if ("payment".equals(tipo)) {
                MercadoPagoNotification notificationFinal = notification.data() != null && notification.data().id() != null
                        ? notification
                        : new MercadoPagoNotification(notification.action(), tipo, notification.topic(), notification.resource(),
                                new MercadoPagoNotification.Data(paymentId));
                webhookService.processarNotificacao(notificationFinal);
            } else if ("merchant_order".equals(tipo)) {
                webhookService.processarMerchantOrder(paymentId);
            } else {
                log.info("Webhook MP ignorado: tipo={} paymentId={}", tipo, paymentId);
            }
        } catch (MPApiException | MPException ex) {
            log.error("Erro de comunicação com MP id={}: {}", paymentId, ex.getMessage());
            return ResponseEntity.internalServerError().build();
        } catch (Exception ex) {
            log.error("Erro ao processar webhook MP id={}: {}", paymentId, ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
