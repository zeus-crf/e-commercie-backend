package com.ecommercie.catalogo.models;

import com.ecommercie.shared.BaseEntity;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "produto_variacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String sku;

    // null + cor null = variacao "unica"
    private String tamanho;

    private String cor;

    @Column(precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(name = "peso_kg")
    private BigDecimal pesoKg;

    @Column(name = "altura_cm")
    private BigDecimal alturaCm;

    @Column(name = "largura_cm")
    private BigDecimal larguraCm;

    @Column(name = "comprimento_cm")
    private BigDecimal comprimentoCm;

    @Builder.Default
    private boolean ativo = true;

    public BigDecimal precoEfetivo() {
        return preco != null ? preco : product.getPreco();
    }

    public BigDecimal pesoEfetivo () {
        return pesoKg != null ? pesoKg : product.getPesoKg();
    }

    public BigDecimal alturaEfetiva() {
        return alturaCm != null ? alturaCm : product.getAlturaCm();
    }

    public BigDecimal larguraEfetiva() {
        return larguraCm != null ? larguraCm : product.getLarguraCm();
    }

    public BigDecimal comprimentoEfetivo() {
        return comprimentoCm != null ? comprimentoCm : product.getComprimentoCm();
    }

    /** "M / Azul", "M", "Azul" ou null (variacao unica). */
    public String descricao() {
        String d = Stream.of(tamanho, cor).filter(Objects::nonNull).collect(Collectors.joining(" / "));
        return d.isEmpty() ? null : d;
    }
}
