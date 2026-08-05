package com.ecommercie.carrinho.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CartItemRequest(
        @NotBlank(message = "O produto não pode ser nulo")
        String product_id,

        @Positive(message = "A quantidade deve ser maior que zero")
        int quantidade
) {
}
