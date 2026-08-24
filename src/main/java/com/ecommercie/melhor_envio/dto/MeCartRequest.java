package com.ecommercie.melhor_envio.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record MeCartRequest(
        Integer service,
        MeAddress from,
        MeAddress to,
        List<MeProduct> products,
        List<MeVolume> volumes
) {

    @Builder
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
            String state_abbr
    ) {}

    @Builder
    public record MeProduct(
            String name,
            int quantity,
            BigDecimal unitary_value
    ) {}

    @Builder
    public record MeVolume(
            BigDecimal weight,
            BigDecimal width,
            BigDecimal height,
            BigDecimal length
    ) {}
}