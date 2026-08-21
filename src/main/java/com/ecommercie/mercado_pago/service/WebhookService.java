package com.ecommercie.mercado_pago.service;

import com.ecommercie.estoque.service.InventoryService;
import com.ecommercie.mercado_pago.WebhookEventRepository;
import com.ecommercie.mercado_pago.dtos.MercadoPagoNotification;
import com.ecommercie.mercado_pago.models.WebhookEvent;
import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.dispatcher.OutboxDispatcher;
import com.ecommercie.outbox.service.OutboxService;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.pedido.repository.OrderRepository;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final OutboxService outboxService;

    @Transactional
    public void processarNotificacao(MercadoPagoNotification request) throws MPException, MPApiException {
        if (webhookEventRepository.existsByProviderAndExternalId("mercadopago", request.data().id())) {
            return;
        }

        PaymentClient client = new PaymentClient();
        Payment payment = client.get(Long.parseLong(request.data().id()));

        // Webhooks chegam para qualquer status (pending, rejected, etc.) — ignoramos os que não são approved
        if (!"approved".equals(payment.getStatus())) {
            return;
        }

        Order order = orderRepository.findById(payment.getExternalReference())
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado: " + payment.getExternalReference()));

        order.markPaid();
        orderRepository.save(order);

        for (OrderItem item : order.getItens()) {
            inventoryService.confirmarBaixa(item.getProduct().getId(), item.getQuantidade());
        }

        outboxService.registrar(
                OutboxTypes.EMAIL_CONFIRMACAO_PEDIDO,
                new OutboxDispatcher.EmailPayload(order.getId(), order.getUser().getEmail())
        );

        webhookEventRepository.save(WebhookEvent.builder()
                .provider("mercadopago")
                .externalId(request.data().id())
                .status("PROCESSED")
                .build());
    }
}
