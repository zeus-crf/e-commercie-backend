package com.ecommercie.pedido.dtos;

import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.pedido.models.StatusOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record OrderResponse(
        String order_id,
        StatusOrder status,
        BigDecimal valorItens,
        BigDecimal valorFrete,
        BigDecimal total,
        AddressResponse endereco,
        List<OrderItemResponse> itens,
        LocalDateTime expiresAt
) {

    public static OrderResponse from(Order order) {
        var total = order.getValorItens().add(order.getValorFrete());

        List<OrderItemResponse> itensResponseList = new ArrayList<>();
        for (OrderItem item : order.getItens()) {
            itensResponseList.add(OrderItemResponse.from(item));
        }

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getValorItens(),
                order.getValorFrete(),
                total,
                AddressResponse.from(order.getAddress()),
                itensResponseList,
                order.getExpiresAt()
        );
    }
}
