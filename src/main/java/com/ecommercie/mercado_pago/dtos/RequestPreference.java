package com.ecommercie.mercado_pago.dtos;

import jakarta.validation.constraints.NotNull;

public record RequestPreference(
        @NotNull BackUrls backUrls,
        DeliveryAddress deliveryAddress
) {

    public record BackUrls(
            String success,
            String failure,
            String pending
    ) {}

    public record DeliveryAddress(
            String zipCode,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state
    ) {}
}
