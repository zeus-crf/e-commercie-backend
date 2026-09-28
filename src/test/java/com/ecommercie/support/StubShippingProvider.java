package com.ecommercie.support;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.EtiquetaRequest;
import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.enums.SituacaoEtiqueta;
import com.ecommercie.melhor_envio.models.Shipment;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stub da API do Melhor Envio. So finge a API: nao muda status nem grava nada no banco
 * (isso e do ShippingService). Registra as chamadas e pode falhar sob demanda.
 * E um singleton do contexto de teste: o DatabaseCleaner.limparTudo() chama reset().
 */
public class StubShippingProvider implements ShippingProvider {

    public static final String TRACKING_CODE = "STUB123456789BR";
    public static final BigDecimal PRECO_PAC = new BigDecimal("25.90");
    public static final String ME_ORDER_PREFIX = "stub-me-order-";

    private final List<EtiquetaRequest> carrinhos = new CopyOnWriteArrayList<>();
    private final List<String> compras = new CopyOnWriteArrayList<>();
    private final List<Boolean> transacaoAtivaNasChamadas = new CopyOnWriteArrayList<>();
    private volatile boolean falharNoCarrinho;
    private volatile boolean falharNaCompra;
    private volatile boolean falharNaCompraSemResposta;
    private volatile Runnable aoCriarCarrinho;
    private final List<String> consultas = new CopyOnWriteArrayList<>();
    private final Map<String, SituacaoEtiqueta> situacoesForcadas = new ConcurrentHashMap<>();
    private volatile boolean falharNaConsulta;
    private volatile boolean cobrarMasPerderResposta;

    @Override
    public List<ShippingQuote> quote(ShippingQuoteRequest request) {
        return List.of(
                new ShippingQuote(2, "PAC", "Correios", PRECO_PAC, 5),
                new ShippingQuote(1, "SEDEX", "Correios", new BigDecimal("39.90"), 2)
        );
    }

    @Override
    public String adicionarAoCarrinho(EtiquetaRequest request) {
        transacaoAtivaNasChamadas.add(TransactionSynchronizationManager.isActualTransactionActive());
        if (falharNoCarrinho) {
            throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "stub: falha ao criar carrinho");
        }
        carrinhos.add(request);
        Runnable gancho = aoCriarCarrinho;
        if (gancho != null) {
            gancho.run();   // simula outra requisicao agindo enquanto esta falava com o ME
        }
        return ME_ORDER_PREFIX + request.orderId();
    }

    @Override
    public void finalizarCompra(String meOrderId) {
        transacaoAtivaNasChamadas.add(TransactionSynchronizationManager.isActualTransactionActive());
        if (falharNaCompra) {
            throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "stub: saldo insuficiente");
        }
        if (falharNaCompraSemResposta) {
            throw new ResourceAccessException("stub: timeout no checkout (nao se sabe se cobrou)");
        }
        if (cobrarMasPerderResposta) {
            compras.add(meOrderId);   // o ME cobrou...
            throw new ResourceAccessException("stub: cobrou, mas a resposta se perdeu");
        }
        compras.add(meOrderId);
    }

    @Override
    public SituacaoEtiqueta consultarSituacao(String meOrderId) {
        transacaoAtivaNasChamadas.add(TransactionSynchronizationManager.isActualTransactionActive());
        consultas.add(meOrderId);
        if (falharNaConsulta) {
            throw new ResourceAccessException("stub: ME fora do ar na consulta");
        }
        SituacaoEtiqueta forcada = situacoesForcadas.get(meOrderId);
        if (forcada != null) {
            return forcada;
        }
        return compras.contains(meOrderId) ? SituacaoEtiqueta.PAGA : SituacaoEtiqueta.PENDENTE_NO_CARRINHO;
    }

    @Override
    public void cancelLabel(Shipment shipment) {
        // no-op: nao ha etiqueta real para cancelar
    }

    public void reset() {
        carrinhos.clear();
        compras.clear();
        transacaoAtivaNasChamadas.clear();
        falharNoCarrinho = false;
        falharNaCompra = false;
        falharNaCompraSemResposta = false;
        aoCriarCarrinho = null;
        consultas.clear();
        situacoesForcadas.clear();
        falharNaConsulta = false;
        cobrarMasPerderResposta = false;
    }

    public void falharNoCarrinho(boolean falhar) { this.falharNoCarrinho = falhar; }
    public void falharNaCompra(boolean falhar) { this.falharNaCompra = falhar; }
    public List<EtiquetaRequest> carrinhos() { return carrinhos; }
    public List<String> compras() { return compras; }
    public List<Boolean> transacaoAtivaNasChamadas() { return transacaoAtivaNasChamadas; }
    public void falharNaCompraSemResposta(boolean falhar) { this.falharNaCompraSemResposta = falhar; }
    public void aoCriarCarrinho(Runnable gancho) { this.aoCriarCarrinho = gancho; }
    public void falharNaConsulta(boolean falhar) { this.falharNaConsulta = falhar; }
    public void cobrarMasPerderResposta(boolean ativo) { this.cobrarMasPerderResposta = ativo; }
    public void situacao(String meOrderId, SituacaoEtiqueta situacao) { situacoesForcadas.put(meOrderId, situacao); }
    public List<String> consultas() { return consultas; }
}
