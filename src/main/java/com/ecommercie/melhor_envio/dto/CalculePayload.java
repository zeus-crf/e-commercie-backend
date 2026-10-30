package com.ecommercie.melhor_envio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CalculePayload(
        PostalCode from,
        PostalCode to,
        List<CalculateItem> products,
        Options options
) {
    public record PostalCode(String postal_code) {}

    /** Valor declarado: o frete cotado ja inclui o seguro, igual ao da etiqueta. */
    public record Options(BigDecimal insurance_value) {}
}
