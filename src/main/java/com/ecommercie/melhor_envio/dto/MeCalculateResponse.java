package com.ecommercie.melhor_envio.dto;

public record MeCalculateResponse(
        Integer id,
        String name,
        MeCompany company,
        String price,         // ME devolve como String
        Integer delivery_time,
        String error
) {
    public record MeCompany(String name) {}
}
