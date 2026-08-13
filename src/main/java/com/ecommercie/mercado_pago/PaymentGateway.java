package com.ecommercie.mercado_pago;

import com.ecommercie.mercado_pago.dtos.PaymentPreference;
import com.ecommercie.pedido.models.Order;

public interface PaymentGateway {

    PaymentPreference criarPreferencia(Order order);
}
