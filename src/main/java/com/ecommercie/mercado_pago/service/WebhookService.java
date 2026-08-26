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
import com.mercadopago.client.merchantorder.MerchantOrderClient;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.merchantorder.MerchantOrder;
import com.mercadopago.resources.merchantorder.MerchantOrderPayment;
import com.mercadopago.resources.payment.Payment;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final OutboxService outboxService;

    @Transactional
    public void processarNotificacao(MercadoPagoNotification request) throws MPException, MPApiException {
        String paymentId = request.data().id();
        log.info("WebhookService: processando payment id={}", paymentId);

        if (webhookEventRepository.existsByProviderAndExternalId("mercadopago", paymentId)) {
            log.info("WebhookService: paymentId={} já processado, ignorando", paymentId);
            return;
        }

        PaymentClient client = new PaymentClient();
        Payment payment = client.get(Long.parseLong(paymentId));

        log.info("WebhookService: paymentId={} status={} externalReference={}", paymentId, payment.getStatus(), payment.getExternalReference());

        if (!"approved".equals(payment.getStatus())) {
            log.info("WebhookService: status não é approved — ignorando");
            return;
        }

        processarPagamentoAprovado(payment.getExternalReference(), paymentId);
    }

    @Transactional
    public void processarMerchantOrder(String merchantOrderId) throws MPException, MPApiException {
        log.info("WebhookService: processando merchant_order id={}", merchantOrderId);

        MerchantOrderClient moClient = new MerchantOrderClient();
        MerchantOrder merchantOrder = moClient.get(Long.parseLong(merchantOrderId));

        List<MerchantOrderPayment> payments = merchantOrder.getPayments();
        if (payments == null || payments.isEmpty()) {
            log.info("WebhookService: merchant_order {} sem pagamentos", merchantOrderId);
            return;
        }

        for (MerchantOrderPayment mp : payments) {
            if (!"approved".equals(mp.getStatus())) {
                continue;
            }
            String paymentId = String.valueOf(mp.getId());
            if (webhookEventRepository.existsByProviderAndExternalId("mercadopago", paymentId)) {
                log.info("WebhookService: paymentId={} já processado, ignorando", paymentId);
                continue;
            }
            log.info("WebhookService: merchant_order {} → payment aprovado id={}", merchantOrderId, paymentId);
            processarPagamentoAprovado(merchantOrder.getExternalReference(), paymentId);
        }
    }

    private void processarPagamentoAprovado(String orderId, String paymentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado: " + orderId));

        order.markPaid();
        order.setMpPaymentId(Long.parseLong(paymentId));
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
                .externalId(paymentId)
                .status("PROCESSED")
                .build());

        log.info("WebhookService: pedido {} marcado como pago (paymentId={})", orderId, paymentId);
    }
}
