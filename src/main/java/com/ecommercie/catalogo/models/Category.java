package com.ecommercie.catalogo.models;

import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "categorias")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Category extends BaseEntity {

    @Column(nullable = false)
    private String nome;

    @Column(unique = true)
    private String slug;
}
