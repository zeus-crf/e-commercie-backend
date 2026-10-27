package com.ecommercie.security.dto;

import com.ecommercie.shared.documento.CpfCnpjValido;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequestUser(
        @NotBlank(message = "O nome não pode ser nulo")
        String nome,

        @NotBlank(message = "O email não pode ser nulo")
        String email,

        @NotBlank(message = "A senha não pode ser nulo")
        String senha,

        @NotBlank(message = "O CPF não pode ser nulo")
        @CpfCnpjValido
        String cpf_cnpj
) {
}
