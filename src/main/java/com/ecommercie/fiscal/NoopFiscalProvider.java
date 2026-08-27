package com.ecommercie.fiscal;

import com.ecommercie.fiscal.dto.FiscalDocument;
import com.ecommercie.fiscal.enums.FiscalDocumentType;
import com.ecommercie.pedido.models.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NoopFiscalProvider implements FiscalProvider{

    @Override
    public FiscalDocument emitir(Order order) {
        log.debug("Fiscal Provider: NOOP - nenhuma documento fiscal emitido para de pedido {}", order.getId());
        return new FiscalDocument(FiscalDocumentType.NOOP, null);
    }
}
