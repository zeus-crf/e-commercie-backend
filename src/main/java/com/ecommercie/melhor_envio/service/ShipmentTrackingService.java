package com.ecommercie.melhor_envio.service;

import com.ecommercie.melhor_envio.dto.MeTrackingEvent;
import com.ecommercie.melhor_envio.models.Shipment;
import com.ecommercie.melhor_envio.models.ShipmentTrackingEvent;
import com.ecommercie.melhor_envio.repository.ShipmentTrackingEventRepository;
import com.ecommercie.melhor_envio.repository.ShippimentRepository;
import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.dispatcher.OutboxDispatcher;
import com.ecommercie.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentTrackingService {

    private final ShippimentRepository shipmentRepository;
    private final ShipmentTrackingEventRepository trackingEventRepository;
    private final OutboxService outboxService;

    @Transactional
    public void processar(MeTrackingEvent event) {
        Shipment shipment = shipmentRepository.findByMeOrderId(event.shipmentId())
                .orElse(null);

        if (shipment == null) {
            log.warn("Tracking recebido para meOrderId desconhecido: {}", event.shipmentId());
            return;
        }

        shipment.setTrackingCode(event.tracking());
        shipment.setTrackingStatus(event.status());

        if ("posted".equals(event.status())) {
            shipment.setPostedAt(LocalDateTime.now());
        } else if ("delivered".equals(event.status())) {
            shipment.setDeliveredAt(LocalDateTime.now());
            try {
                shipment.getOrder().markEntregue();
                // so chega aqui se a transicao valeu: "delivered" repetido cai no catch e nao duplica o e-mail
                outboxService.registrar(OutboxTypes.EMAIL_PEDIDO_ENTREGUE,
                        new OutboxDispatcher.EmailPayload(shipment.getOrder().getId(), shipment.getOrder().getUser().getEmail()));
            } catch (IllegalArgumentException ex) {
                log.warn("Não foi possível marcar pedido {} como entregue: {}", shipment.getOrder().getId(), ex.getMessage());
            }
        }

        shipmentRepository.save(shipment);

        trackingEventRepository.save(ShipmentTrackingEvent.builder()
                .shipment(shipment)
                .status(event.status())
                .trackingCode(event.tracking())
                .occurredAt(event.createdAt() != null ? event.createdAt() : LocalDateTime.now())
                .build());

        log.info("Tracking processado - pedido={} status={} tracking={}", shipment.getOrder().getId(), event.status(), event.tracking());
    }
}
