package com.ecommercie.outbox;

/**
 * Tipos de evento do outbox. Usados por quem GRAVA a intenção e por quem LÊ (dispatcher)
 * — a mesma constante nos dois lados evita divergência de string.
 */
public final class OutboxTypes {

    public static final String EMAIL_CONFIRMACAO_PEDIDO = "EMAIL_CONFIRMACAO_PEDIDO";
    public static final String EMAIL_PEDIDO_ENVIADO = "EMAIL_PEDIDO_ENVIADO";
    public static final String EMAIL_PEDIDO_CANCELADO = "EMAIL_PEDIDO_CANCELADO";
    public static final String EMAIL_PEDIDO_ENTREGUE = "EMAIL_PEDIDO_ENTREGUE";

    private OutboxTypes() {}
}
