package com.ecommercie.mercado_pago;

import com.ecommercie.mercado_pago.models.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {

    boolean existsByProviderAndExternalId(String provider, String externalId);
}
