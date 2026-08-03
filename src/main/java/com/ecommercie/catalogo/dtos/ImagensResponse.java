package com.ecommercie.catalogo.dtos;

import com.ecommercie.catalogo.models.ProductImage;

public record ImagensResponse(
        String url,
        int ordem
) {
    public static ImagensResponse from(ProductImage img) {
        return new ImagensResponse(img.getUrl(), img.getOrdem());
    }
}
