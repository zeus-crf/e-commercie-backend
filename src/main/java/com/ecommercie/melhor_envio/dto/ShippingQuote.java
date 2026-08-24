package com.ecommercie.melhor_envio.dto;

import java.math.BigDecimal;

public record ShippingQuote(
        int serviceId,
        String serviceName,
        String company,
        BigDecimal price,
        int deadlineDays
) {
}
