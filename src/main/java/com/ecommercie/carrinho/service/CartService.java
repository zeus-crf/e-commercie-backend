package com.ecommercie.carrinho.service;

import com.ecommercie.carrinho.dto.CartItemRequest;
import com.ecommercie.carrinho.dto.CartResponse;
import com.ecommercie.carrinho.model.Cart;
import com.ecommercie.carrinho.model.CartItem;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.models.ProductVariant;
import com.ecommercie.catalogo.repository.ProductVariantRepository;
import com.ecommercie.estoque.repository.InventoryItemRepository;
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
    private final InventoryItemRepository inventoryItemRepository;
    private final ProductVariantRepository variantRepository;

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

        ProductVariant variant = variantRepository.findById(request.variant_id())
                .orElseThrow(() -> new EntityNotFoundException("Essa variação de produto não existe"));

        if (!variant.isAtivo() || !variant.getProduct().isAtivo()) {
            throw new IllegalArgumentException("Esta variação do produto não está disponível");
        }

        var estoque = inventoryItemRepository.findByVariantId(variant.getId())
                .orElseThrow(() -> new EntityNotFoundException("Estoque não encontrado para o produto"));

        Optional<CartItem> existente = carrinho.getItens().stream()
                .filter(i -> i.getVariant().getId().equals(variant.getId()))
                .findFirst();

        int quantidadeTotal = request.quantidade() + existente.map(CartItem::getQuantidade).orElse(0);
        int disponivel = estoque.getDisponivel() - estoque.getReservada();
        if (quantidadeTotal > disponivel) {
            throw new IllegalArgumentException("Quantidade solicitada indisponível em estoque");
        }

        if (existente.isPresent()) {
            existente.get().setQuantidade(existente.get().getQuantidade() + request.quantidade());
        } else {
            carrinho.getItens().add(CartItem.builder()
                    .cart(carrinho)
                    .variant(variant)
                    .quantidade(request.quantidade())
                    .build());
        }

        // flush: garante o INSERT (e o id via @PrePersist) do item novo antes de montar
        // a resposta
        cartRepository.saveAndFlush(carrinho);

        return CartResponse.from(carrinho);

    }

    @Transactional
    public CartResponse editItem(User user, CartItemRequest request) {

        var carrinho = buscarCarrinho(user);

        ProductVariant variant = variantRepository.findById(request.variant_id())
                .orElseThrow(() -> new EntityNotFoundException("Essa variação de produto não existe"));

        if (!variant.isAtivo() || !variant.getProduct().isAtivo()) {
            throw new IllegalArgumentException("Esta variação do produto não está disponível");
        }

        var estoque = inventoryItemRepository.findByVariantId(variant.getId())
                .orElseThrow(() -> new EntityNotFoundException("Estoque não encontrado para o produto"));

        int disponivel = estoque.getDisponivel() - estoque.getReservada();
        if (request.quantidade() > disponivel) {
            throw new IllegalArgumentException("Quantidade solicitada indisponível em estoque");
        }

        Optional<CartItem> existente = carrinho.getItens().stream()
                .filter(i -> i.getVariant().getId().equals(variant.getId()))
                .findFirst();

        if (existente.isPresent()) {
            existente.get().setQuantidade(request.quantidade());
        } else {
            throw new EntityNotFoundException("Essa variação de produto não está no carrinho");
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
