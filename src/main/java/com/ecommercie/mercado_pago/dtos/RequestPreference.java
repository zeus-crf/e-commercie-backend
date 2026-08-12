package com.ecommercie.mercado_pago.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record RequestPreference(
        String userId,

        @DecimalMin(value = "0.01", message = "O valor total deve ser maior do que zero")
        BigDecimal totalAmount,

        @NotBlank
        Payer payer,

        @NotNull
        BackUrls backUrls,

        @NotNull
        DeliveryAddress deliveryAddress,

        @NotNull
        String notificationUrl,

        @NotNull
        List<ItemDto> itemsDto
) {

    public record Payer (
            @NotBlank
            String nome,

            @NotBlank
            String email
    ){}

    public record BackUrls(
            String success,
            String failure,
            String pending
    ){}

    public record DeliveryAddress(
            String zipCoce,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state
    ){}

    public record ItemDto(
            String id,
            String title,
            String description,
            int quantity,
            BigDecimal unitPrice
    ){}
}
