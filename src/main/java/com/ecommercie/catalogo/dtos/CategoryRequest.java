package com.ecommercie.catalogo.dtos;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank(message = "O nome não pode nulo")
        String nome,

        @NotBlank(message = "O slug não pode ser nulo")
        String slug
) {
}
