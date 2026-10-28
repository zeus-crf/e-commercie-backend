package com.ecommercie.security.dto;

import com.ecommercie.shared.documento.CpfCnpjValido;
import jakarta.validation.constraints.NotBlank;

public record AtualizarCpfRequest(
        @NotBlank(message = "O CPF não pode ser nula")
        @CpfCnpjValido
        String cpf_cnpj
) {
}
