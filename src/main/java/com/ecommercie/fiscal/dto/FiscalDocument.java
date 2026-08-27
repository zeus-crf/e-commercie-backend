package com.ecommercie.fiscal.dto;

import com.ecommercie.fiscal.enums.FiscalDocumentType;

public record FiscalDocument(FiscalDocumentType type, String chave) {
    public boolean hasKey(){
        return type != FiscalDocumentType.NOOP && chave != null && !chave.isBlank();
    }
}
