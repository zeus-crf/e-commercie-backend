package com.ecommercie.melhor_envio.dto;

import java.util.List;

public record ShippingQuoteRequest(
        String cepDestino,
        List<ShippingItem> itens
) {
}
