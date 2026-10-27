package com.ecommercie.melhor_envio;

import com.ecommercie.melhor_envio.dto.Cancelabilidade;
import com.ecommercie.melhor_envio.dto.EtiquetaRequest;
import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.enums.SituacaoEtiqueta;
import com.ecommercie.melhor_envio.models.Shipment;

import java.util.List;

public interface ShippingProvider {
    List<ShippingQuote> quote(ShippingQuoteRequest request);

    /** Cria o item no carrinho do ME (nao cobra). Devolve o id do item, gravado como me_order_id. */
    String adicionarAoCarrinho(EtiquetaRequest request);

    /** Compra a etiqueta do item (COBRA o saldo do ME). */
    void finalizarCompra(String meOrderId);

    /**
     * Gera a etiqueta ja paga (avisa a transportadora). Lanca GeracaoRecusadaException se o ME recusar;
     * RestClientException se nao houver resposta (nao da para saber se gerou).
     */
    void gerarEtiqueta(String meOrderId);

    /** Link privado de impressao da etiqueta ja gerada. Nao cobra nem muda nada no ME. */
    String linkImpressao(String meOrderId);



    SituacaoEtiqueta consultarSituacao(String meOrderId);

    Cancelabilidade consultarCancelamento(String meOrderId);

    /** Cancela a etiqueta. Lanca CancelamentoRecusadoException se o ME recusar. */
    void cancelarEtiqueta(String meOrderId, String motivo);
}
