package com.ecommercie.melhor_envio.models;

import com.ecommercie.pedido.models.Order;
import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "envios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    private Order order;

    @Column(name = "me_order_id")
    private String meOrderId;

    @Column(name = "me_protocol")
    private String meProtocol;

    @Column(name = "service_id")
    private Integer serviceId;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Column(name = "tracking_status")
    private String trackingStatus;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "label_generated_at")
    private LocalDateTime labelGeneratedAt;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
}
