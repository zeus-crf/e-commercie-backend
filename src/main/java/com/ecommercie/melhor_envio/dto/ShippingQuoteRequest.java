package com.ecommercie.melhor_envio.dto;

import java.util.List;

public record ShippingQuoteRequest(
        String cepDestino,
        List<ShippingItem> itens
) {
    /** Soma de preco x quantidade dos itens que informaram o preco; null quando nenhum informou. */
    public java.math.BigDecimal valorSegurado() {
        if (itens == null) return null;
        java.math.BigDecimal total = itens.stream()
                .filter(i -> i.precoUnitario() != null)
                .map(i -> i.precoUnitario().multiply(java.math.BigDecimal.valueOf(i.quantidade())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return total.signum() > 0 ? total : null;
    }
}
