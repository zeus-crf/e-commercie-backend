package com.ecommercie.fiscal.dto;

import com.ecommercie.fiscal.enums.FiscalDocumentType;

public record FiscalDocument(FiscalDocumentType tipo, String chave) {
    public boolean hasKey(){
        return tipo != FiscalDocumentType.NOOP && chave != null && !chave.isBlank();
    }
}
