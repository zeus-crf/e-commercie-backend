package com.ecommercie.carrinho.dto;

import com.ecommercie.carrinho.model.CartItem;

import java.math.BigDecimal;

public record CartItemResponse(
        String itemId,
        String productId,
        String nome,
        BigDecimal preco,
        int quantidade,
        BigDecimal subtotal
) {
    public static CartItemResponse from (CartItem cartItem) {
        BigDecimal subtotal = cartItem.getProduct().getPreco().multiply(BigDecimal.valueOf(cartItem.getQuantidade()));
        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getProduct().getId(),
                cartItem.getProduct().getNome(),
                cartItem.getProduct().getPreco(),
                cartItem.getQuantidade(),
                subtotal
        );
    }
}
