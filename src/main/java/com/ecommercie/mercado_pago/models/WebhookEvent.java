package com.ecommercie.mercado_pago.models;

import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_event")
public class WebhookEvent extends BaseEntity {
    private String provider;
    private String externalId;
    private String status;
}
