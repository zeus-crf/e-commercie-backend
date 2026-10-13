package com.ecommercie.fiscal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PoliticaFiscal {

    private final boolean ligado;

    public PoliticaFiscal(@Value("${features.fiscal.enabled:false}") boolean ligado) {
        this.ligado = ligado;
    }

    public boolean exigeDocumentoNaEtiqueta() {
        return ligado;
    }
}
