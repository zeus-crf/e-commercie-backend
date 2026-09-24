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
import org.springframework.transaction.support.TransactionTemplate;

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
    // o Spring Boot ja registra um TransactionTemplate para o transaction manager do JPA
    private final TransactionTemplate transactionTemplate;

    public List<ShippingQuote> quote(ShippingQuoteRequest request){
        return shippingProvider.quote(request);
    }

    /**
     * Gera a etiqueta sem segurar transacao do banco durante as chamadas ao Melhor Envio:
     *
     *   1. [tx] prepara: valida, separa o pedido e fotografa os dados (EtiquetaRequest)
     *   2.      carrinho no ME (nao cobra)
     *   3. [tx] grava o envio PENDENTE com o me_order_id, antes de pagar
     *   4.      compra no ME (COBRA o saldo)
     *   5. [tx] conclui: etiqueta gerada + pedido ENVIADO + e-mail
     *
     * Se algo falhar depois do passo 3, o retry encontra o envio pendente e reaproveita o
     * carrinho (pula o passo 2) em vez de comprar outra etiqueta.
     */
    public Shipment gerarEtiqueta(String orderId) {
        Preparo preparo = transactionTemplate.execute(tx -> preparar(orderId));

        String meOrderId = preparo.meOrderIdPendente();
        if (meOrderId == null) {
            String novo = shippingProvider.adicionarAoCarrinho(preparo.request());
            transactionTemplate.executeWithoutResult(tx -> registrarPendente(orderId, novo));
            meOrderId = novo;
        }

        shippingProvider.finalizarCompra(meOrderId);

        return transactionTemplate.execute(tx -> concluir(orderId));
    }

    private record Preparo(EtiquetaRequest request, String meOrderIdPendente) {}

    private Preparo preparar(String orderId) {
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

        String pendente = shippimentRepository.findByOrderId(orderId)
                .map(Shipment::getMeOrderId)
                .orElse(null);

        EtiquetaRequest request = EtiquetaRequest.from(order, order.getShippingServiceId(),
                fiscalService.buscarPorPedido(orderId));
        return new Preparo(request, pendente);
    }

    private void registrarPendente(String orderId, String meOrderId) {
        Order order = orderRepository.getReferenceById(orderId);
        shippimentRepository.save(Shipment.builder()
                .order(order)
                .meOrderId(meOrderId)
                .serviceId(order.getShippingServiceId())
                .build());   // labelGeneratedAt null = pendente
    }

    private Shipment concluir(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        Shipment shipment = shippimentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Envio pendente não encontrado para o pedido " + orderId));
        shipment.setLabelGeneratedAt(LocalDateTime.now());
        orderService.marcarEnviado(order);
        return shipment;
    }
}
