package com.ecommercie.estoque.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InventoryItemRequest(
        @NotBlank(message = "O produto não pode ser nulo")
        String product_id,

        @NotNull(message = "O número de item não pode ser menor que 0")
        int disponivel,

        @NotNull(message = "O número de item não pode ser menor que 0")
        int reservada
) {
}
