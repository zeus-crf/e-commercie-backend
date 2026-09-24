package com.ecommercie.melhor_envio.service;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.models.Shipment;
import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.dispatcher.OutboxDispatcher;
import com.ecommercie.outbox.service.OutboxService;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingProvider shippingProvider;
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;

    public List<ShippingQuote> quote(ShippingQuoteRequest request){
        return shippingProvider.quote(request);
    }

    @Transactional
    public Shipment gerarEtiqueta(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        if (order.getShippingServiceId() == null) {
            throw new IllegalStateException("Pedido não possui transportadora selecionada (shippingServiceId null). Use um pedido feito após a seleção de frete.");
        }
        Shipment shipment = shippingProvider.buyLabel(order, order.getShippingServiceId());
        // o buyLabel ja marcou o pedido como ENVIADO (ver pendencia no README: a transicao deveria estar aqui)
        outboxService.registrar(OutboxTypes.EMAIL_PEDIDO_ENVIADO,
                new OutboxDispatcher.EmailPayload(order.getId(), order.getUser().getEmail()));
        return shipment;
    }

}
