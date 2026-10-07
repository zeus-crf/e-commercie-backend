package com.ecommercie.shared;

/**
        * A operação conflita com outra em andamento sobre o mesmo recurso (ex.: etiqueta já sendo
        * gerada para o pedido). Vira HTTP 409 no GlobalExceptionHandler.
        */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String message) {
        super(message);
    }
}
