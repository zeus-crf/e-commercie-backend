package com.ecommercie.catalogo.dtos;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "A categoria não pode nula")
        String category_id,

        @NotBlank(message = "O nome não pode ser nulo")
        String nome,

        @NotNull(message = "O preço não pode nulo")
        @DecimalMin(value = "0.01", message = "O preço deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O preço deve ter no máximo 10 dígitos inteiros e 2 casas decimais")
        BigDecimal preco,

        @NotNull(message = "O peso não pode ser nulo")
        @DecimalMin(value = "0.01", message = "O peso deve ser maior que 0")
        BigDecimal peso_kg,

        @NotNull(message = "A altura não pode ser nula")
        @DecimalMin(value = "0.01", message = "A altura deve ser maior que 0")
        BigDecimal altura_cm,

        @NotNull(message = "A largura não pode ser nula")
        @DecimalMin(value = "0.01", message = "A largura deve ser maior que 0")
        BigDecimal largura_cm,

        @NotNull(message = "O comprimento não pode ser nulo")
        @DecimalMin(value = "0.01", message = "O comprimento deve ser maior que 0")
        BigDecimal comprimento_cm,

        String ncm,

        String cfop,

        String origem
) {
}
