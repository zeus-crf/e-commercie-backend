package com.ecommercie.carrinho.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CartItemRequest(
                @NotBlank(message = "A variação do produto não pode ser nula") String variant_id,

                @Positive(message = "A quantidade deve ser maior que zero") int quantidade

) {
}
