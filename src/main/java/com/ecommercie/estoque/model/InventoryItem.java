package com.ecommercie.estoque.model;

import com.ecommercie.catalogo.models.Product;
import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory_item")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryItem extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    @Builder.Default
    private int disponivel = 0;

    @Column(nullable = false)
    @Builder.Default
    private int reservada = 0;
}

