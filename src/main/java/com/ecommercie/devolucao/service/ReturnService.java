package com.ecommercie.devolucao.service;

import com.ecommercie.estoque.service.InventoryService;
import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.dispatcher.OutboxDispatcher;
import com.ecommercie.outbox.service.OutboxService;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.security.models.User;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final OutboxService outboxService;
    private final PaymentRefundClient paymentRefundClient;
    private final TransactionTemplate transactionTemplate;

    static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

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


    /**
     * Estorna sem segurar transacao do banco durante a chamada ao Mercado Pago:
     *
     *   1. [tx] valida: o pedido esta DEVOLUCAO_SOLICITADA e tem pagamento no MP
     *   2.      estorno no MP, com chave de idempotencia por pedido
     *   3. [tx] conclui: devolve o estoque, marca DEVOLVIDO e registra o e-mail
     *
     * A validacao vem ANTES do estorno: antes, um pedido em qualquer status era estornado e so
     * depois recusado. Se o passo 3 falhar, o retry repete o estorno com a mesma chave e o MP
     * devolve o estorno ja feito, em vez de estornar de novo.
     */
    public void confirmarDevolucao(String orderId) throws MPException, MPApiException {
        Long mpPaymentId = transactionTemplate.execute(tx -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
            exigirDevolucaoSolicitada(order);
            if (order.getMpPaymentId() == null) {
                throw new IllegalStateException("O pedido não tem pagamento no Mercado Pago para estornar");
            }
            return order.getMpPaymentId();
        });

        try {
            paymentRefundClient.refund(mpPaymentId, MPRequestOptions.builder()
                    .customHeaders(Map.of(IDEMPOTENCY_HEADER, chaveDeEstorno(orderId)))
                    .build());
        } catch (MPApiException e) {
            log.error("Mercado Pago recusou o estorno do pedido {}: status={} body={}", orderId, e.getStatusCode(),
                    e.getApiResponse() != null ? e.getApiResponse().getContent() : e.getMessage());
            throw e;
        }

        transactionTemplate.executeWithoutResult(tx -> {
            // lock: duas confirmacoes simultaneas nao devolvem o estoque duas vezes; a segunda ve DEVOLVIDO
            Order order = orderRepository.findWithLockById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
            exigirDevolucaoSolicitada(order);
            for (OrderItem item : order.getItens()) {
                if (item.getVariant() != null) {
                    inventoryService.estornarBaixa(item.getVariant().getId(), item.getQuantidade());
                }
            }
            order.markDevolucao();
            order.setRefundedAt(LocalDateTime.now());
            outboxService.registrar(OutboxTypes.EMAIL_REEMBOLSO_CONFIRMADO,
                    new OutboxDispatcher.EmailPayload(order.getId(), order.getUser().getEmail()));
        });
    }

    static String chaveDeEstorno(String orderId) {
        return "estorno-" + orderId;
    }

    private static void exigirDevolucaoSolicitada(Order order) {
        if (order.getStatus() != StatusOrder.DEVOLUCAO_SOLICITADA) {
            throw new IllegalArgumentException("Só um pedido que teve uma devolução solicitada pode ser devolvido");
        }
    }
}
