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
import com.ecommercie.shared.ConflitoException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
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
    static final Duration JANELA_CHECKOUT = Duration.ofMinutes(5);

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
            meOrderId = shippingProvider.adicionarAoCarrinho(preparo.request());
            registrarPendenteReservado(orderId, meOrderId);
        } else {
            reservarCheckout(orderId);
        }

        comprar(orderId, meOrderId);

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
                .filter(envio -> envio.getLabelGeneratedAt() == null)
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

    private void registrarPendenteReservado(String orderId, String meOrderId) {
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                Order order = orderRepository.getReferenceById(orderId);
                shippimentRepository.saveAndFlush(Shipment.builder()
                        .order(order)
                        .meOrderId(meOrderId)
                        .serviceId(order.getShippingServiceId())
                        .checkoutIniciadoEm(LocalDateTime.now())   // nasce reservado para esta requisicao
                        .build());                                 // labelGeneratedAt null = pendente
            });
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(pedido_id): outra requisicao gravou o envio deste pedido depois do passo 1.
            // O carrinho criado por esta fica orfao no ME, mas nao e cobrado.
            throw new ConflitoException("A etiqueta deste pedido já está sendo gerada. Aguarde e confira o status do pedido.");
        }
    }

    private void reservarCheckout(String orderId) {
        LocalDateTime agora = LocalDateTime.now();
        Integer reservados = transactionTemplate.execute(tx ->
                shippimentRepository.reservarCheckout(orderId, agora, agora.minus(JANELA_CHECKOUT)));
        if (reservados == null || reservados == 0) {
            throw new ConflitoException("A etiqueta deste pedido já está sendo gerada ou aguarda confirmação do Melhor Envio. "
                    + "Confira no painel do ME antes de tentar de novo.");
        }
    }

    private void comprar(String orderId, String meOrderId) {
        try {
            shippingProvider.finalizarCompra(meOrderId);
        } catch (HttpClientErrorException e) {
            // 4xx: o ME recusou e nao cobrou. Descarta o pendente para o retry montar um carrinho novo
            transactionTemplate.executeWithoutResult(tx -> shippimentRepository.descartarPendente(orderId));
            throw e;
        } catch (RestClientException e) {
            // 5xx / timeout: nao da para saber se cobrou. O pendente fica reservado (bloqueia retry
            // imediato durante a JANELA_CHECKOUT) e o admin confere no painel do ME
            throw new ConflitoException("Sem resposta confirmada do Melhor Envio ao comprar a etiqueta. "
                    + "Confira no painel do ME se ela foi paga antes de tentar de novo.");
        }
    }
}
