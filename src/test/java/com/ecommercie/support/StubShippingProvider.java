package com.ecommercie.support;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.EtiquetaRequest;
import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.models.Shipment;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.List;
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
        return ME_ORDER_PREFIX + request.orderId();
    }

    @Override
    public void finalizarCompra(String meOrderId) {
        transacaoAtivaNasChamadas.add(TransactionSynchronizationManager.isActualTransactionActive());
        if (falharNaCompra) {
            throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "stub: saldo insuficiente");
        }
        compras.add(meOrderId);
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
    }

    public void falharNoCarrinho(boolean falhar) { this.falharNoCarrinho = falhar; }
    public void falharNaCompra(boolean falhar) { this.falharNaCompra = falhar; }
    public List<EtiquetaRequest> carrinhos() { return carrinhos; }
    public List<String> compras() { return compras; }
    public List<Boolean> transacaoAtivaNasChamadas() { return transacaoAtivaNasChamadas; }
}
