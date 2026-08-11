package com.ecommercie.pedido.service;

import com.ecommercie.estoque.service.InventoryService;
import com.ecommercie.pedido.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;


    public OrderResponse checkout(String userId) {

    }
}
