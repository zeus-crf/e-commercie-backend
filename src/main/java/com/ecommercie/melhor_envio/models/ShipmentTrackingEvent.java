package com.ecommercie.melhor_envio.models;

import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipment_tracking_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentTrackingEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(nullable = false)
    private String status;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
