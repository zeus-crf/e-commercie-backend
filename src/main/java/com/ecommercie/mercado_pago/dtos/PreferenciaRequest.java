package com.ecommercie.mercado_pago.dtos;

import com.ecommercie.pedido.models.Order;

import java.math.BigDecimal;
import java.util.List;

public record PreferenciaRequest(
        String orderId,
        String compradorNome,
        String compradorEmail,
        List<Item> itens,
        RequestPreference.BackUrls backUrls
) {
    public record Item(String id, String nome, String descricao, int quantidade, BigDecimal precoUnitario) {}

    public static PreferenciaRequest from(Order order, RequestPreference request) {
        return new PreferenciaRequest(order.getId(), order.getUser().getNome(), order.getUser().getEmail(),
                order.getItens().stream()
                        .map(i -> new Item(i.getId(), i.getNomeProduto(),
                                i.getProduct() != null ? i.getProduct().getDescricao() : null,
                                i.getQuantidade(), i.getPrecoUnitario()))
                        .toList(),
                request.backUrls());
    }
}
