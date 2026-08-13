package com.ecommercie.mercado_pago.dtos;

public record PaymentPreference(
        String preferenceId,
        String redirectUrl
) {
}
