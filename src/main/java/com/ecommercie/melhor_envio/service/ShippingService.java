package com.ecommercie.melhor_envio.service;

import com.ecommercie.fiscal.service.FiscalService;
import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.EtiquetaRequest;
import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.models.Shipment;
import com.ecommercie.melhor_envio.repository.ShippimentRepository;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.pedido.service.OrderService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingProvider shippingProvider;
    private final OrderRepository orderRepository;
    private final ShippimentRepository shippimentRepository;
    private final OrderService orderService;
    private final FiscalService fiscalService;

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
        // valida ANTES de chamar o ME: nada de comprar etiqueta para pedido que nao pode ser enviado
        if (order.getStatus() != StatusOrder.PAGO && order.getStatus() != StatusOrder.EM_SEPARACAO) {
            throw new IllegalStateException("Só é possível gerar etiqueta para pedido pago ou em separação (status atual: " + order.getStatus() + ")");
        }
        if (order.getStatus() == StatusOrder.PAGO) {
            orderService.marcarSeparando(order);
        }

        EtiquetaRequest request = EtiquetaRequest.from(order, order.getShippingServiceId(),
                fiscalService.buscarPorPedido(orderId));
        String meOrderId = shippingProvider.adicionarAoCarrinho(request);
        shippingProvider.finalizarCompra(meOrderId);

        Shipment shipment = shippimentRepository.save(Shipment.builder()
                .order(order)
                .meOrderId(meOrderId)
                .serviceId(order.getShippingServiceId())
                .labelGeneratedAt(LocalDateTime.now())
                .build());

        orderService.marcarEnviado(order);
        return shipment;
    }
}
