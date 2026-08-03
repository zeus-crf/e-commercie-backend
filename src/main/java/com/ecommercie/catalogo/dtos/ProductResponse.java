package com.ecommercie.catalogo.dtos;

import com.ecommercie.catalogo.models.Product;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        String id,
        String category_id,
        String nome,
        BigDecimal preco,
        BigDecimal peso_kg,
        BigDecimal altura_cm,
        BigDecimal largura_cm,
        BigDecimal comprimento_cm,
        List<ImagensResponse> imagens,
        String ncm,
        String cfop,
        String origem
) {

    public static ProductResponse from (Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getNome(),
                product.getPreco(),
                product.getPesoKg(),
                product.getAlturaCm(),
                product.getLarguraCm(),
                product.getComprimentoCm(),
                product.getImagens().stream().map(ImagensResponse::from).toList(),
                product.getNcm(),
                product.getCfop(),
                product.getOrigem()
        );
    }
}
