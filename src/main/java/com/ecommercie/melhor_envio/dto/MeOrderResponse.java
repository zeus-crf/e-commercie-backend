package com.ecommercie.melhor_envio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeOrderResponse(
        String id,
        String status,
        @JsonProperty("paid_at") String paidAt,
        @JsonProperty("canceled_at") String canceledAt,
        @JsonProperty("expired_at") String expiredAt
) {
}
