package com.ecommercie.carrinho.service;

import com.ecommercie.carrinho.dto.CartItemRequest;
import com.ecommercie.carrinho.dto.CartResponse;
import com.ecommercie.carrinho.model.Cart;
import com.ecommercie.carrinho.model.CartItem;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.security.models.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;


    @Transactional
    public CartResponse getOrCreate(User user) {

        Cart carrinho = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(
                        Cart.builder().user(user).build()));
        
        return CartResponse.from(carrinho);
    }

    @Transactional
    public CartResponse addItem(User user, CartItemRequest request) {

        var carrinho = buscarCarrinho(user);

        Product product = productRepository.findById(request.product_id())
                .orElseThrow(() -> new EntityNotFoundException("Esse produto não existe"));


        Optional<CartItem> existente = carrinho.getItens().stream()
                .filter(i -> i.getProduct().getId().equals(product.getId()))
                .findFirst();

        if (existente.isPresent()) {
            existente.get().setQuantidade(existente.get().getQuantidade() + request.quantidade());
        } else {
            carrinho.getItens().add(CartItem.builder()
                    .cart(carrinho)
                    .product(product)
                    .quantidade(request.quantidade())
                    .build() );
        }

        return CartResponse.from(carrinho);

    }


    @Transactional
    public CartResponse editItem (User user, CartItemRequest request) {

        var carrinho = buscarCarrinho(user);

        Product product = productRepository.findById(request.product_id())
                .orElseThrow(() -> new EntityNotFoundException("Esse produto não existe"));

        Optional<CartItem> existente = carrinho.getItens().stream()
                .filter(i -> i.getProduct().getId().equals(product.getId()))
                .findFirst();

        if (existente.isPresent()) {
            existente.get().setQuantidade(request.quantidade());
        } else {
            throw new EntityNotFoundException("Produto não encontrado");
        }

        return CartResponse.from(carrinho);
    }


    @Transactional
    public CartResponse removeItem(User user, String itemId) {
        var carrinho = buscarCarrinho(user);

        Optional<CartItem> existente = carrinho.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst();

        if (existente.isPresent()) {
            carrinho.getItens().remove(existente.get());
        } else {
            throw new EntityNotFoundException("Esse produto não está na lista");
        }

        return CartResponse.from(carrinho);
    }



    private Cart buscarCarrinho(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));
    }
}
