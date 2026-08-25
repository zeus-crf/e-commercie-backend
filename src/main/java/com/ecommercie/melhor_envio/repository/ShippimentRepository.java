package com.ecommercie.melhor_envio.repository;

import com.ecommercie.melhor_envio.models.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippimentRepository extends JpaRepository<Shipment, String> {
}
