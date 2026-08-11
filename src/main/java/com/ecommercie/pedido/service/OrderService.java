package com.ecommercie.pedido.service;

import com.ecommercie.carrinho.model.CartItem;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.estoque.service.InventoryService;
import com.ecommercie.pedido.dtos.CheckoutRequest;
import com.ecommercie.pedido.dtos.OrderResponse;
import com.ecommercie.pedido.models.Address;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.security.models.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final CartRepository cartRepository;

    @Transactional
    public OrderResponse checkout(User user, CheckoutRequest request) {

        Order order = new Order();
        var carrinho = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Carrinho não encontrado"));

        if (carrinho.getItens().isEmpty()){
            throw new IllegalArgumentException("O carrinho está vazio");
        }

        Address address = new Address(
                request.logradouro(),
                request.numero(),
                request.bairro(),
                request.cidade(),
                request.uf(),
                request.cep()
        );

        List<OrderItem> orderItemList = new ArrayList<>();

        for (CartItem item : carrinho.getItens()) {
            inventoryService.reservarItem(item.getProduct().getId(), item.getQuantidade());
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(item.getProduct())
                    .nomeProduto(item.getProduct().getNome())
                    .precoUnitario(item.getProduct().getPreco())  
                    .quantidade(item.getQuantidade())
                    .build();
            orderItemList.add(orderItem);
        }


       BigDecimal valorItens = orderItemList.stream()
                       .map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())))
                               .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setAddress(address);
        order.setItens(orderItemList);
        order.setUser(user);
        order.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        order.setValorItens(valorItens);
        order.setValorFrete(BigDecimal.ZERO);


        orderRepository.save(order);

        carrinho.getItens().clear();

        return OrderResponse.from(order);

    }
    @Transactional(readOnly = true)
    public Page<OrderResponse> listarMeusPedidos(User user, Pageable pageable) {
        return orderRepository.findByUserId(user.getId(), pageable)
                .map(OrderResponse::from);
    }


    @Transactional(readOnly = true)
    public OrderResponse meuPedido(User user, String orderId) {
        return orderRepository.findByIdAndUserId(orderId, user.getId())
                .map(OrderResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listarTodos(StatusOrder status, Pageable pageable) {
        var page = (status != null)
                ? orderRepository.findByStatus(status, pageable)
                : orderRepository.findAll(pageable);

        return page.map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listarPorCliente(String userId, StatusOrder status, Pageable pageable) {
        var page = (status != null)
                ? orderRepository.findByUserIdAndStatus(userId, status, pageable)
                : orderRepository.findByUserId(userId, pageable);

        return page.map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse separar(String orderId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Esse pedido não existe"));

        order.markSeparando();
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse enviar(String orderId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        order.markEnviando();
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse entregar(String orderId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        order.markEntregue();
        return OrderResponse.from(order);
    }


    @Transactional
    public OrderResponse cancelar(User user, String orderId) {
        var order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));
        cancelarEDevolver(order);
        return OrderResponse.from(order);
    }

    // expira os pedidos AGUARDANDO_PAGAMENTO vencidos (disparado pelo job)
    @Transactional
    public void expirarPedidosVencidos() {
        var vencidos = orderRepository.findByStatusAndExpiresAtBefore(
                StatusOrder.AGUARDANDO_PAGAMENTO, LocalDateTime.now());
        vencidos.forEach(this::cancelarEDevolver);
    }

    // cancela o pedido e devolve a reserva de estoque de cada item.
    // reusado pelo cancelar (cliente) e pela expiração (job).
    private void cancelarEDevolver(Order order) {
        order.markCancelado();
        for (OrderItem item : order.getItens()) {
            if (item.getProduct() != null) {
                inventoryService.devolverReserva(item.getProduct().getId(), item.getQuantidade());
            }
        }
    }
}
