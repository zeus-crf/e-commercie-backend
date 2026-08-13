package com.ecommercie.mercado_pago.service;

import com.ecommercie.carrinho.model.Cart;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.mercado_pago.client.MercadoPagoClient;
import com.ecommercie.mercado_pago.dtos.RequestPreference;
import com.ecommercie.mercado_pago.dtos.ResponsePreference;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.UserRepository;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PreferenceService {

    private final MercadoPagoClient mercadoPagoClient;


    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;


    public ResponsePreference createdPreference(RequestPreference request, String orderId) throws MPException {

        User user = null;
        Cart carrinho = null;

        if (request.userId() != null) {

            user = userRepository.findById(request.userId())
                    .orElseThrow(() ->
                            new EntityNotFoundException("Usuário não encontrado"));

            carrinho = cartRepository.findByUserId(request.userId())
                    .orElseThrow(() ->
                            new EntityNotFoundException("Carrinho não encontrado"));
        }




        for (RequestPreference.ItemDto itemDto : request.itemsDto()) {
            var product = productRepository.findById(itemDto.id())
                    .orElseThrow(() -> new EntityNotFoundException("Esse produto não exite"));
        }

        try {
            ResponsePreference response = mercadoPagoClient.createPreference(request, orderId);
        } catch (MPApiException ex) {
            log.error("Erro ao criar preferência na API do mercado pago: {}", ex.getMessage());
        }
        catch (MPException ex) {
            log.error("Erro ao criar preferência no Mercado Pago: {}", ex.getMessage());
            throw ex;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return null;
    }

}
