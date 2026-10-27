package com.ecommercie.melhor_envio;

public class CancelamentoRecusadoException extends RuntimeException {
    public CancelamentoRecusadoException(String motivo) {
        super(motivo);
    }
}
