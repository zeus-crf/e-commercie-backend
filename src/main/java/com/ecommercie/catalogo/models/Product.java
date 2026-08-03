package com.ecommercie.catalogo.models;

import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "produtos")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Column(nullable = false)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private BigDecimal preco;

    @Builder.Default
    @Column(nullable = false)
    private boolean ativo = true;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // dimensoes para o frete (Melhor Envio)
    @Column(name = "peso_kg", nullable = false)
    private BigDecimal pesoKg;

    @Column(name = "altura_cm", nullable = false)
    private BigDecimal alturaCm;

    @Column(name = "largura_cm", nullable = false)
    private BigDecimal larguraCm;

    @Column(name = "comprimento_cm", nullable = false)
    private BigDecimal comprimentoCm;

    // fiscais (NF-e, Fase 10) - preenchidos por padrao, mas nullable por ora
    private String ncm;

    private String cfop;

    private String origem;
}
