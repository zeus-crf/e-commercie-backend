package com.ecommercie.fiscal;

import com.ecommercie.fiscal.dto.FiscalDocument;
import com.ecommercie.pedido.models.Order;

public interface FiscalProvider  {
    FiscalDocument emitir(Order order);
}
