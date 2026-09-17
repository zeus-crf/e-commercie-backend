package com.ecommercie.fiscal.outbox;

import javax.swing.plaf.PanelUI;

public final class OutboxTypes {

    public static final String EMAIL_CONFIRMACAO_PEDIDO = "EMAIL_CONFIRMACAO_PEDIDO";
    public static final String EMAIL_PEDIDO_ENVIADO = "EMAIL_PEDIDO_ENVIADO";
    public static final String EMAIL_PEDIDO_CANCELADO = "EMAIL_PEDIDO_CANCELADO";
    public static final String EMAIL_PEDIDO_ENTREGUE = "EMAIL_PEDIDO_ENTREGUE";
    public static final String EMAIL_REEMBOLSO_CONFIRMADO = "EMAIL_REEMBOLSO_CONFIRMADO";
    public static final String EMAIL_REEMBOLSO_SOLICITADO = "EMAIL_REEMBOLSO_SOLICITADO";
    public static final String FISCAL_EMISSAO = "FISCAL_EMISSAO";


    private OutboxTypes(){}
}
