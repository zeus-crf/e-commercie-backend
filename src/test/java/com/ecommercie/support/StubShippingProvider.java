package com.ecommercie.support;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.models.Shipment;
import com.ecommercie.melhor_envio.repository.ShippimentRepository;
import com.ecommercie.pedido.models.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Stub do frete. Espelha os efeitos colaterais do MelhorEnvioClient.buyLabel, que alem de chamar
 * a API faz as transicoes do pedido: PAGO -> EM_SEPARACAO -> ENVIADO, e persiste o Shipment.
 * Sem isso o fluxo ponta a ponta nunca chega em ENVIADO.
 */
public class StubShippingProvider implements ShippingProvider {

    public static final String TRACKING_CODE = "STUB123456789BR";
    public static final BigDecimal PRECO_PAC = new BigDecimal("25.90");

    private final ShippimentRepository shippimentRepository;

    public StubShippingProvider(ShippimentRepository shippimentRepository) {
        this.shippimentRepository = shippimentRepository;
    }

    @Override
    public List<ShippingQuote> quote(ShippingQuoteRequest request) {
        return List.of(
                new ShippingQuote(2, "PAC", "Correios", PRECO_PAC, 5),
                new ShippingQuote(1, "SEDEX", "Correios", new BigDecimal("39.90"), 2)
        );
    }

    @Override
    public Shipment buyLabel(Order order, int serviceId) {
        order.markSeparando();

        Shipment shipment = shippimentRepository.save(Shipment.builder()
                .order(order)
                .meOrderId("stub-me-order-" + order.getId())
                .meProtocol("stub-protocol")
                .serviceId(serviceId)
                .trackingCode(TRACKING_CODE)
                .trackingStatus("posted")
                .price(PRECO_PAC)
                .labelGeneratedAt(LocalDateTime.now())
                .build());

        order.markEnviando();
        return shipment;
    }

    @Override
    public void cancelLabel(Shipment shipment) {
        // no-op: nao ha etiqueta real para cancelar
    }
}
