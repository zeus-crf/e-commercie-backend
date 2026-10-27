package com.ecommercie.melhor_envio.service;

import com.ecommercie.fiscal.PoliticaFiscal;
import com.ecommercie.fiscal.enums.InvoiceStatus;
import com.ecommercie.fiscal.model.Invoice;
import com.ecommercie.fiscal.service.FiscalService;
import com.ecommercie.melhor_envio.CancelamentoRecusadoException;
import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.Cancelabilidade;
import com.ecommercie.melhor_envio.dto.EtiquetaRequest;
import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.enums.SituacaoEtiqueta;
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
import com.ecommercie.melhor_envio.GeracaoRecusadaException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private final PoliticaFiscal politicaFiscal;
    private static final String MOTIVO_PADRAO_CANCELAMENTO = "Cancelada pelo lojista";

    public List<ShippingQuote> quote(ShippingQuoteRequest request){
        return shippingProvider.quote(request);
    }

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

        Shipment envio = shippimentRepository.findAtivoByOrderId(orderId).orElse(null);
        if (envio != null && envio.getEtiquetaGeradaEm() != null) {
            throw new IllegalStateException("A etiqueta deste pedido já foi gerada. Imprima com GET /api/v1/admin/orders/"
                    + orderId + "/label/print.");
        }
        if (envio != null && envio.getEtiquetaPagaEm() != null) {
            // paga numa tentativa anterior, mas a geracao nao terminou: o retry so gera
            return new Preparo(null, null, envio.getMeOrderId());
        }
        Optional<Invoice> documentoFiscal = fiscalService.buscarPorPedido(orderId);
        if (politicaFiscal.exigeDocumentoNaEtiqueta()
                && documentoFiscal.filter(inv -> inv.getStatus() == InvoiceStatus.AUTORIZADO).isEmpty()) {
            // a chave do documento vai no carrinho do ME: sem ela a etiqueta sairia sem nota
            throw new IllegalStateException("O documento fiscal deste pedido ainda não foi autorizado. Confira em GET /api/v1/admin/orders/"
                    + orderId + "/invoice e gere a etiqueta depois da emissão.");
        }

        if (order.getStatus() == StatusOrder.PAGO) {
            orderService.marcarSeparando(order);
        }

        String pendente = envio != null ? envio.getMeOrderId() : null;   // aqui, se existe, o envio e pendente
        EtiquetaRequest request = EtiquetaRequest.from(order, order.getShippingServiceId(), documentoFiscal);
        return new Preparo(request, pendente, null);
    }

    private Shipment concluir(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        Shipment shipment = shippimentRepository.findAtivoByOrderId(order.getId())
                .orElseThrow(() -> new IllegalStateException("Envio pendente não encontrado para o pedido " + orderId));
        shipment.setEtiquetaPagaEm(LocalDateTime.now());
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
                        .build());                                 // etiquetaPagaEm null = pendente
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

    private SituacaoEtiqueta consultarNoMe (String orderId, String meOrderId) {
        SituacaoEtiqueta situacao;

        try {
            situacao = shippingProvider.consultarSituacao(meOrderId);
        } catch (RestClientException e) {
            situacao = SituacaoEtiqueta.INDEFINIDA;
        }
        if (situacao == SituacaoEtiqueta.INDEFINIDA) {
            // sem resposta confiavel nada e pago no escuro; a reserva e solta para o admin tentar em instantes
            transactionTemplate.executeWithoutResult(tx -> shippimentRepository.liberarReserva(orderId));
            throw new ConflitoException("Não foi possível confirmar no Melhor Envio a situação da etiqueta deste pedido. "
                    + "Tente de novo em instantes ou confira no painel do ME.");
        }
        return situacao;
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

    /** Gera a etiqueta ja paga. Fora de transacao: e uma chamada HTTP que avisa a transportadora. */
    private Shipment gerar(String orderId, String meOrderId) {
        try {
            shippingProvider.gerarEtiqueta(meOrderId);
        } catch (GeracaoRecusadaException e) {
            throw new IllegalStateException("A etiqueta foi paga, mas o Melhor Envio recusou gerá-la: " + e.getMessage()
                    + " Corrija e clique de novo: ela não será paga outra vez.");
        } catch (RestClientException e) {
            throw new ConflitoException("A etiqueta foi paga, mas não houve resposta do Melhor Envio ao gerá-la. "
                    + "Clique de novo em instantes: ela não será paga outra vez.");
        }
        return registrarGeracao(orderId);
    }

    private Shipment registrarGeracao(String orderId) {
        return transactionTemplate.execute(tx -> {
            Shipment envio = shippimentRepository.findAtivoByOrderId(orderId)
                    .orElseThrow(() -> new IllegalStateException("Envio não encontrado para o pedido " + orderId));
            envio.setEtiquetaGeradaEm(LocalDateTime.now());
            return envio;
        });
    }

    /**
     * Retry de uma etiqueta paga cuja geracao nao terminou. Confere no ME antes: a geracao pode ter dado
     * certo (resposta perdida) ou o dono pode ter gerado pelo painel.
     */
    private Shipment retomarGeracao(String orderId, String meOrderId) {
        SituacaoEtiqueta situacao;
        try {
            situacao = shippingProvider.consultarSituacao(meOrderId);
        } catch (RestClientException e) {
            situacao = SituacaoEtiqueta.INDEFINIDA;
        }
        return switch (situacao) {
            case GERADA -> registrarGeracao(orderId);
            case PAGA -> gerar(orderId, meOrderId);
            case CANCELADA -> {
                transactionTemplate.executeWithoutResult(tx ->
                        shippimentRepository.marcarCancelada(orderId, LocalDateTime.now(), "Cancelada no Melhor Envio"));
                throw new IllegalStateException("A etiqueta deste pedido foi cancelada no Melhor Envio. "
                        + "Clique de novo para gerar uma nova (ela será paga outra vez).");
            }
            default -> throw new ConflitoException("Não foi possível confirmar no Melhor Envio a situação da etiqueta deste pedido. "
                    + "Tente de novo em instantes ou confira no painel do ME.");
        };
    }

    /**
     * Paga e gera a etiqueta sem segurar transacao do banco durante as chamadas ao Melhor Envio:
     *
     *   1. [tx] prepara: valida, separa o pedido e fotografa os dados (EtiquetaRequest)
     *   2.      carrinho no ME (nao cobra)
     *   3. [tx] grava o envio PENDENTE com o me_order_id, antes de pagar
     *   4.      compra no ME (COBRA o saldo)
     *   5. [tx] conclui: etiqueta_paga_em
     *   6.      gera no ME (avisa a transportadora)
     *   7. [tx] etiqueta_gerada_em
     *
     * Se algo falhar depois do passo 3, o retry encontra o envio pendente e reaproveita o carrinho.
     * Se falhar no passo 6, o retry encontra o envio PAGO e so gera: nunca paga de novo.
     * O pedido segue EM_SEPARACAO ate o ME avisar a postagem (webhook "posted").
     */
    public Shipment gerarEtiqueta(String orderId) {
        Preparo preparo = transactionTemplate.execute(tx -> preparar(orderId));

        if (preparo.meOrderIdPagoSemGerar() != null) {
            return retomarGeracao(orderId, preparo.meOrderIdPagoSemGerar());
        }

        String meOrderId = preparo.meOrderIdPendente();
        if (meOrderId != null) {
            reservarCheckout(orderId);
            SituacaoEtiqueta situacao = consultarNoMe(orderId, meOrderId);
            if (situacao == SituacaoEtiqueta.PAGA || situacao == SituacaoEtiqueta.GERADA) {
                // o ME ja cobrou numa tentativa anterior (a resposta se perdeu): nao paga de novo
                transactionTemplate.executeWithoutResult(tx -> concluir(orderId));
                return situacao == SituacaoEtiqueta.GERADA ? registrarGeracao(orderId) : gerar(orderId, meOrderId);
            }
            if (situacao == SituacaoEtiqueta.CANCELADA) {
                transactionTemplate.executeWithoutResult(tx -> shippimentRepository.descartarPendente(orderId));
                meOrderId = null;   // segue como se nao houvesse pendente: etiqueta nova no carrinho do ME
            }
        }
        if (meOrderId == null) {
            meOrderId = shippingProvider.adicionarAoCarrinho(preparo.request());
            registrarPendenteReservado(orderId, meOrderId);
        }
        comprar(orderId, meOrderId);
        transactionTemplate.executeWithoutResult(tx -> concluir(orderId));
        return gerar(orderId, meOrderId);
    }

    public String linkImpressao(String orderId) {
        String meOrderId = shippimentRepository.findAtivoByOrderId(orderId)
                .filter(envio -> envio.getEtiquetaGeradaEm() != null)
                .map(Shipment::getMeOrderId)
                .orElseThrow(() -> new IllegalStateException("A etiqueta deste pedido ainda não foi gerada. "
                        + "Gere com POST /api/v1/admin/orders/" + orderId + "/label."));
        return shippingProvider.linkImpressao(meOrderId);
    }

    private record Preparo(EtiquetaRequest request, String meOrderIdPendente, String meOrderIdPagoSemGerar) {}


    public void cancelarEtiqueta(String orderId, String motivoInformado) {
        String motivo = motivoInformado == null || motivoInformado.isBlank() ? MOTIVO_PADRAO_CANCELAMENTO : motivoInformado;
        String meOrderId = transactionTemplate.execute(tx -> {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
            if (order.getStatus() != StatusOrder.EM_SEPARACAO){
                throw  new IllegalStateException("Só é possível cancelar a etiqueta de um pedido em separação(status atual: " + order.getStatus() +
                        "). Depois da postagem, o cancelamento é pelo painel do Melhor Envio");
            }

            return shippimentRepository.findAtivoByOrderId(orderId)
                    .filter(envio -> envio.getEtiquetaPagaEm() != null)   // paga basta: gerada ou nao
                    .map(Shipment::getMeOrderId)
                    .orElseThrow(() -> new IllegalStateException("Este pedido não tem etiqueta paga para cancelar."));
        });
        Cancelabilidade cancelabilidade = shippingProvider.consultarCancelamento(meOrderId);
        if (!cancelabilidade.cancelavel()) {
            throw new IllegalStateException("O Melhor Envio não permite mais cancelar esta etiqueta"
                    + (cancelabilidade.motivo().isBlank() ? "." : ": " + cancelabilidade.motivo()));
        }
        try {
            shippingProvider.cancelarEtiqueta(meOrderId, motivo);
        } catch (CancelamentoRecusadoException e) {
            throw new IllegalStateException("O Melhor Envio recusou o cancelamento da etiqueta: " + e.getMessage());
        }

        transactionTemplate.executeWithoutResult(tx ->
                shippimentRepository.marcarCancelada(orderId, LocalDateTime.now(), motivo));

    }





}
