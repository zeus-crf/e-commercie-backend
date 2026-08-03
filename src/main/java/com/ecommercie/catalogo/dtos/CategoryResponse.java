package com.ecommercie.catalogo.dtos;

import com.ecommercie.catalogo.models.Category;

public record CategoryResponse(
        String id,
        String nome,
        String slug
) {

    public static  CategoryResponse from(Category category) {
        return  new CategoryResponse(
                category.getId(),
                category.getNome(),
                category.getSlug()
        );
    }
}
