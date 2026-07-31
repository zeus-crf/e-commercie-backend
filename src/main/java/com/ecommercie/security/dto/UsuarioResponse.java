package com.ecommercie.security.dto;

import jakarta.validation.constraints.NotBlank;

public record UsuarioResponse(
        @NotBlank(message = "O nome não pode ser nulo")
        String nome,

        @NotBlank(message = "O email não pode ser nulo")
        String email
) {
}
