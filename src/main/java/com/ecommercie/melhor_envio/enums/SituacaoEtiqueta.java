package com.ecommercie.melhor_envio.enums;

public enum SituacaoEtiqueta {
    PENDENTE_NO_CARRINHO,
    PAGA,
    GERADA,      // paga e gerada: transportadora avisada, pronta para imprimir
    CANCELADA,
    INDEFINIDA
}
