package com.ecommercie.melhor_envio.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CalculateItem(
        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal length,
        int quantity
) {
}
