package com.ecommercie.melhor_envio;

/** O ME respondeu, mas recusou gerar a etiqueta (dado invalido, documento fiscal faltando...). Nada foi gerado. */
public class GeracaoRecusadaException extends RuntimeException {
    public GeracaoRecusadaException(String motivo) {
        super(motivo);
    }
}
