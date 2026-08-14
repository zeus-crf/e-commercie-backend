package com.ecommercie.mercado_pago.service;

import com.ecommercie.mercado_pago.PaymentGateway;
import com.ecommercie.mercado_pago.dtos.PaymentPreference;
import com.ecommercie.mercado_pago.dtos.RequestPreference;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.UserRepository;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PreferenceService {

    private final PaymentGateway paymentGateway;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public PaymentPreference criarPreferencia(RequestPreference request, String orderId) throws MPException, MPApiException {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Pedido não pertence ao usuário");
        }

        return paymentGateway.criarPreferencia(order, request);
    }
}
