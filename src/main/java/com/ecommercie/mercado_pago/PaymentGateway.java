package com.ecommercie.mercado_pago;

import com.ecommercie.mercado_pago.dtos.PaymentPreference;
import com.ecommercie.mercado_pago.dtos.RequestPreference;
import com.ecommercie.pedido.models.Order;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;

public interface PaymentGateway {

    PaymentPreference criarPreferencia(Order order, RequestPreference request) throws MPException, MPApiException;
}
