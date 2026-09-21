package com.ecommercie.support;

import com.ecommercie.mercado_pago.PaymentGateway;
import com.ecommercie.mercado_pago.dtos.PaymentPreference;
import com.ecommercie.mercado_pago.dtos.RequestPreference;
import com.ecommercie.pedido.models.Order;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;

/** Stub do gateway de pagamento: nao toca a rede. Preferencia deterministica pelo id do pedido. */
public class StubPaymentGateway implements PaymentGateway {

    public static final String PREFERENCE_ID_PREFIX = "stub-pref-";

    @Override
    public PaymentPreference criarPreferencia(Order order, RequestPreference request) throws MPException, MPApiException {
        return new PaymentPreference(
                PREFERENCE_ID_PREFIX + order.getId(),
                "https://stub.local/checkout/" + order.getId());
    }
}
