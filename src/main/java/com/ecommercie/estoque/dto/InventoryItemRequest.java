package com.ecommercie.estoque.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record InventoryItemRequest(

        @PositiveOrZero(message = "O estoque não pode ser negativo")
        int disponivel
) {
}
