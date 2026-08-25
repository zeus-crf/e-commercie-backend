package com.ecommercie.melhor_envio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeCartRequest(
        Integer service,
        MeAddress from,
        MeAddress to,
        List<MeProduct> products,
        List<MeVolume> volumes,
        @JsonProperty("insurance_value") BigDecimal insurance_value
) {

    public record MeAddress(
            String name,
            String phone,
            String email,
            String document,
            String postal_code,
            String address,
            String number,
            String district,
            String city,
            String state_abbr,
            String country_id
    ) {}

    public record MeProduct(
            String name,
            int quantity,
            BigDecimal unitary_value
    ) {}

    public record MeVolume(
            BigDecimal weight,
            BigDecimal width,
            BigDecimal height,
            BigDecimal length
    ) {}
}
