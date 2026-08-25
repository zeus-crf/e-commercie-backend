package com.ecommercie.melhor_envio.dto;

import com.ecommercie.pedido.models.Order;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EtiquetaRequest(

        @NotBlank
        Order order,

        @NotNull
        int serviceId
) {
}
