package com.ecommercie.melhor_envio.dto;

import java.util.List;

public record CalculePayload(
        PostalCode from,
        PostalCode to,
        List<CalculateItem> products
) {
    public record PostalCode(String postal_code) {}
}
