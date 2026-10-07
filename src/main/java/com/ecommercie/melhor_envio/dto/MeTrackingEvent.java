package com.ecommercie.melhor_envio.dto;

/**
 * Corpo do webhook de etiqueta do Melhor Envio:
 * {"event":"order.posted","data":{"id":"...","status":"posted","tracking":"...", ...}}
 * Doc: https://docs.melhorenvio.com.br/docs/webhooks — campos nao usados sao ignorados.
 */
public record MeTrackingEvent(String event, Data data) {

    public record Data(String id, String status, String tracking){}

    public String meOrderId() {
        return data == null ? null : data().id();
    }

    public String status() {
        return data == null ? null : data.status();
    }

    public String tracking() {
        return data == null ? null : data.tracking();
    }
}
