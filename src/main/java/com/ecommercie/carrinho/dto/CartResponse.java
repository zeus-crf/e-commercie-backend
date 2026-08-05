package com.ecommercie.carrinho.dto;

import com.ecommercie.carrinho.model.Cart;
import com.ecommercie.carrinho.model.CartItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record CartResponse(
        String cart_id,
        String user_id,
        List<CartItemResponse> itens,
        BigDecimal subtotal
) {
    public static CartResponse from (Cart cart) {

        BigDecimal subtotal = cart.getItens().stream()
                .map(cartItem -> cartItem.getProduct().getPreco()
                        .multiply(BigDecimal.valueOf(cartItem.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CartItemResponse> itensResponse = new ArrayList<>();

        for (CartItem item : cart.getItens()) {
            itensResponse.add(CartItemResponse.from(item));
        }

        return new CartResponse(
                cart.getId(),
                cart.getUser().getId(),
                itensResponse,
                subtotal
        );
    }
}
