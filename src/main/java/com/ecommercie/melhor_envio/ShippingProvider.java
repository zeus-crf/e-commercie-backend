package com.ecommercie.melhor_envio;

import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.models.Shipment;
import com.ecommercie.pedido.models.Order;

import java.util.List;

public interface ShippingProvider {
    List<ShippingQuote> quote(ShippingQuoteRequest request);

    Shipment buyLabel(Order order, int serviceId);
    void cancelLabel(Shipment shipment);
}
