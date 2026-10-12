package com.ecommercie.pedido.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CheckoutRequest(
        @NotBlank String logradouro,
        @NotBlank String numero,
        @NotBlank String bairro,
        @NotBlank String cidade,
        // o endereco vira snapshot do pedido e vai para o Melhor Envio na etiqueta:
        // um CEP/UF invalido so apareceria la, quando o admin tentasse gerar a etiqueta
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "A UF deve ter 2 letras maiúsculas (ex.: RJ)") String uf,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "O CEP deve ter 8 dígitos, sem hífen") String cep,
        @NotNull Integer serviceId,
        @NotNull BigDecimal valorFrete
) {}
