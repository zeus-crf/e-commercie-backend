package com.ecommercie.mercado_pago;

import com.ecommercie.mercado_pago.dtos.PaymentPreference;
import com.ecommercie.mercado_pago.dtos.PreferenciaRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;

public interface PaymentGateway {

    PaymentPreference criarPreferencia(PreferenciaRequest request) throws MPException, MPApiException;
}
