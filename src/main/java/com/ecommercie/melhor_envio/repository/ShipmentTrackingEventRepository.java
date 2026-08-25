package com.ecommercie.melhor_envio.repository;

import com.ecommercie.melhor_envio.models.ShipmentTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentTrackingEventRepository extends JpaRepository<ShipmentTrackingEvent, String> {}
