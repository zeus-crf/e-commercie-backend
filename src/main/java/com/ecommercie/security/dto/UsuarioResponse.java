package com.ecommercie.security.dto;

import com.ecommercie.security.models.User;
import jakarta.validation.constraints.NotBlank;

public record UsuarioResponse(
        @NotBlank(message = "O nome não pode ser nulo")
        String nome,

        @NotBlank(message = "O email não pode ser nulo")
        String email
) {

    public static UsuarioResponse from(User user) {
      return new UsuarioResponse(user.getNome(), user.getEmail());
    }
}
