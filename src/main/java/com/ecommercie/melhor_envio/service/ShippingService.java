package com.ecommercie.melhor_envio.service;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.models.Shipment;
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

    public List<ShippingQuote> quote(ShippingQuoteRequest request){
        return shippingProvider.quote(request);
    }

    @Transactional
    public Shipment gerarEtiqueta(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        return shippingProvider.buyLabel(order, order.getShippingServiceId());
    }

}
