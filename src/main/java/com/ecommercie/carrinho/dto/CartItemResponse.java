package com.ecommercie.carrinho.dto;

import com.ecommercie.carrinho.model.CartItem;

import java.math.BigDecimal;

public record CartItemResponse(
        String itemId,
        String productId,
        String variantId,
        String nome,
        String tamanho,
        String cor,
        BigDecimal preco,
        int quantidade,
        BigDecimal subtotal
) {
    public static CartItemResponse from (CartItem cartItem) {
        var v = cartItem.getVariant();
        BigDecimal subtotal = v.precoEfetivo().multiply(BigDecimal.valueOf(cartItem.getQuantidade()));
        return new CartItemResponse(
                cartItem.getId(),
                v.getProduct().getId(),
                v.getId(),
                v.getProduct().getNome(),
                v.getTamanho(),
                v.getCor(),
                v.precoEfetivo(),
                cartItem.getQuantidade(),
                subtotal
        );
    }
}
