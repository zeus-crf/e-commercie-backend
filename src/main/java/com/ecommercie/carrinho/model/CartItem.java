package com.ecommercie.carrinho.model;

import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.models.ProductVariant;
import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "carrinho_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    private int quantidade;
}
