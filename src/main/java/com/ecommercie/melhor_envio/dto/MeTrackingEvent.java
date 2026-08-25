package com.ecommercie.melhor_envio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record MeTrackingEvent(
        @JsonProperty("shipment_id") String shipmentId,
        String status,
        String tracking,
        @JsonProperty("created_at") LocalDateTime createdAt
) {}
