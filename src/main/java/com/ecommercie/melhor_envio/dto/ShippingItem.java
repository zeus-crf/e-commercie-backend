package com.ecommercie.melhor_envio.dto;

import java.math.BigDecimal;

public record ShippingItem(
        BigDecimal pesoKg,
        BigDecimal alturaCm,
        BigDecimal larguraCm,
        BigDecimal comprimentoCm,
        int quantidade,
        BigDecimal precoUnitario   // opcional: com ele a cotacao ja considera o seguro
) {
}
