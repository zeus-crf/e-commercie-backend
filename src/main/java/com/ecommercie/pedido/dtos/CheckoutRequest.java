package com.ecommercie.pedido.dtos;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank String logradouro,
        @NotBlank String numero,
        @NotBlank String bairro,
        @NotBlank String cidade,
        @NotBlank String uf,
        @NotBlank String cep
) {}
