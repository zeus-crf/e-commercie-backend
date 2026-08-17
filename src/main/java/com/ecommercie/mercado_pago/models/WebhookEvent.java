package com.ecommercie.mercado_pago.models;

import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;

@Entity
@Table(name = "webhook_event")
@Builder
public class WebhookEvent extends BaseEntity {
    private String provider;
    private String externalId;
    private String status;
}
