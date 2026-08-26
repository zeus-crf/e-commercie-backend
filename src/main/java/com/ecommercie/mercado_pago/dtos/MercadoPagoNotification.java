package com.ecommercie.mercado_pago.dtos;

public record MercadoPagoNotification(
        String action,
        String type,
        String topic,
        String resource,
        Data data
) {

    // Retorna o tipo normalizado: suporta formato webhook ("type") e IPN ("topic")
    public String tipoNormalizado() {
        if (type != null) return type;
        if (topic != null) return topic;
        return null;
    }

    public record Data(
            String id
    ){}
}
