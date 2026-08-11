package com.ecommercie.pedido;

import com.ecommercie.pedido.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gatilho de expiração de pedidos. A regra (buscar vencidos + cancelar + devolver
 * reserva) mora no OrderService; aqui é só o agendamento.
 */
@Component
@RequiredArgsConstructor
public class OrderExpirationJob {

    private final OrderService orderService;

    @Scheduled(fixedRate = 60000)   // a cada 1 minuto
    public void expirarPedidos() {
        orderService.expirarPedidosVencidos();
    }
}
