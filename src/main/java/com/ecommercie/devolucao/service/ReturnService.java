package com.ecommercie.devolucao.service;

import com.ecommercie.estoque.service.InventoryService;
import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.dispatcher.OutboxDispatcher;
import com.ecommercie.outbox.service.OutboxService;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.security.models.User;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final OutboxService outboxService;
    private final PaymentRefundClient paymentRefundClient;

    @Transactional
    public void devolucaoSolicitada(User user, String orderId, String motivo) {
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        order.markDevolucaoSolicitada();
        order.setReturnReason(motivo);
        order.setReturnRequestedAt(LocalDateTime.now());
        orderRepository.save(order);

        outboxService.registrar(OutboxTypes.EMAIL_REEMBOLSO_SOLICITADO, new OutboxDispatcher.EmailPayload(order.getId(), order.getUser().getEmail()));
    }


    @Transactional
    public void confirmarDevolucao(String orderId) throws MPException, MPApiException {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        try {
            paymentRefundClient.refund(order.getMpPaymentId());
            for (OrderItem item : order.getItens()) {
                inventoryService.estornarBaixa(item.getProduct().getId(), item.getQuantidade());
            }

            order.markDevolucao();
            order.setRefundedAt(LocalDateTime.now());
            orderRepository.save(order);


            outboxService.registrar(OutboxTypes.EMAIL_REEMBOLSO_CONFIRMADO, new OutboxDispatcher.EmailPayload(order.getId(), order.getUser().getEmail()));

        } catch (MPApiException e) {
            log.error("MP refund error: status={} body={}", e.getApiResponse().getStatusCode(), e.getApiResponse().getContent());
            PaymentClient paymentClient = new PaymentClient();

            Payment payment = paymentClient.get(order.getMpPaymentId());

            log.info("Payment ID: {}", payment.getId());
            log.info("Status: {}", payment.getStatus());
            log.info("Status detail: {}", payment.getStatusDetail());

            throw e;
        }


    }
}
