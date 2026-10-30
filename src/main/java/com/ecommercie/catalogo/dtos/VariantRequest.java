package com.ecommercie.catalogo.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VariantRequest(
        @Size(max = 64, message = "O SKU deve ter no máximo 64 caracteres")
        String sku,

        @Size(max = 20, message = "O tamanho deve ter no máximo 20 caracteres")
        String tamanho,

        @Size(max = 40, message = "A cor deve ter no máximo 40 caracteres")
        String cor,

        @DecimalMin(value = "0.01", message = "O preço deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "O preço deve ter no máximo 10 dígitos inteiros e 2 casas decimais")
        BigDecimal preco,

        @DecimalMin(value = "0.01", message = "O peso deve ser maior que 0")
        BigDecimal peso_kg,

        @DecimalMin(value = "0.01", message = "A altura deve ser maior que 0")
        BigDecimal altura_cm,

        @DecimalMin(value = "0.01", message = "A largura deve ser maior que 0")
        BigDecimal largura_cm,

        @DecimalMin(value = "0.01", message = "O comprimento deve ser maior que 0")
        BigDecimal comprimento_cm,

        Boolean ativo
) {
}