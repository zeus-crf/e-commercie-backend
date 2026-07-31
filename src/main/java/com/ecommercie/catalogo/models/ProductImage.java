package com.ecommercie.catalogo.models;

import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "produto_imagens")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductImage extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private String url;

    private int ordem;
}
