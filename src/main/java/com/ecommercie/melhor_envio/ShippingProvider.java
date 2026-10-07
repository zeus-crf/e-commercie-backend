package com.ecommercie.melhor_envio;

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

    void cancelLabel(Shipment shipment);

    SituacaoEtiqueta consultarSituacao(String meOrderId);
}
