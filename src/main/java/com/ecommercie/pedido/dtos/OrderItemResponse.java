package com.ecommercie.pedido.dtos;

import com.ecommercie.pedido.models.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        String variant_id, // pode ser null se o produto foi deletado
        String nomeProduto, // do snapshot
        BigDecimal precoUnitario,
        int quantidade,
        String tamanho,
        String cor,
        BigDecimal subtotal) {
    public static OrderItemResponse from(OrderItem item) {

        var subtotal = item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()));

        return new OrderItemResponse(
                item.getVariant() != null ? item.getVariant().getId() : null, // produto pode ter sido deletado
                item.getNomeProduto(),
                item.getPrecoUnitario(),
                item.getQuantidade(),
                item.getTamanho(),
                item.getCor(),
                subtotal);
    }
}