package com.ecommercie.mercado_pago.dtos;

public record MercadoPagoNotification(
        String action,
        String type,
        Data data
) {

    public record Data(
            String id
    ){}
}
