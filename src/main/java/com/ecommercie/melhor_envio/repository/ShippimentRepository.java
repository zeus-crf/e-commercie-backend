package com.ecommercie.melhor_envio.repository;

import com.ecommercie.melhor_envio.models.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShippimentRepository extends JpaRepository<Shipment, String> {
    Optional<Shipment> findByMeOrderId(String meOrderId);
}
