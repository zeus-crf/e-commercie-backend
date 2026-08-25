package com.ecommercie.melhor_envio.dto;

import java.math.BigDecimal;

public record CalculateItem(
        BigDecimal weight,
        BigDecimal width,
        BigDecimal height,
        BigDecimal length,
        int quantity
) {
    public static CalculateItem of(BigDecimal weight, BigDecimal width, BigDecimal height, BigDecimal length, int quantity) {
        return new CalculateItem(weight, width, height, length, quantity);
    }
}
