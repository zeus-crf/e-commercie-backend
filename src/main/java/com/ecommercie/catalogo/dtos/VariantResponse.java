package com.ecommercie.catalogo.dtos;

import com.ecommercie.catalogo.models.ProductVariant;

import java.math.BigDecimal;

public record VariantResponse(
        String id,
        String sku,
        String tamanho,
        String cor,
        BigDecimal preco,
        BigDecimal peso_kg,
        BigDecimal altura_cm,
        BigDecimal largura_cm,
        BigDecimal comprimento_cm,
        boolean ativo
) {

    public static VariantResponse from(ProductVariant v) {
        return new VariantResponse(v.getId(), v.getSku(), v.getTamanho(), v.getCor(), v.precoEfetivo(),
                v.pesoEfetivo(), v.alturaEfetiva(), v.larguraEfetiva(), v.comprimentoEfetivo(), v.isAtivo());
    }
}
