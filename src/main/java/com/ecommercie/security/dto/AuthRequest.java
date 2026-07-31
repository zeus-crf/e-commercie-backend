package com.ecommercie.security.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
        @NotBlank(message = "O email não pode ser nulo")
        String email,

        @NotBlank(message = "A senha não pode ser nula")
        String senha
        ) {
}
