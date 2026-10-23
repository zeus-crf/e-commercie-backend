package com.ecommercie.mercado_pago.service;

import com.ecommercie.mercado_pago.PaymentGateway;
import com.ecommercie.mercado_pago.dtos.PaymentPreference;
import com.ecommercie.mercado_pago.dtos.PreferenciaRequest;
import com.ecommercie.mercado_pago.dtos.RequestPreference;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.repository.OrderRepository;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class PreferenceService {

    private final PaymentGateway paymentGateway;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;

    public PaymentPreference criarPreferencia(RequestPreference request, String orderId) throws MPException, MPApiException {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        PreferenciaRequest retrato = transactionTemplate.execute(tx -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
            if (!order.getUser().getEmail().equals(email)) {
                throw new AccessDeniedException("Pedido não pertence ao usuário");
            }
            if (order.getStatus() != StatusOrder.AGUARDANDO_PAGAMENTO) {   // item 6
                throw new IllegalStateException("Este pedido não está aguardando pagamento (status: " + order.getStatus() + ")");
            }
            return PreferenciaRequest.from(order, request);
        });
        return paymentGateway.criarPreferencia(retrato);   // fora da transacao
    }
}
