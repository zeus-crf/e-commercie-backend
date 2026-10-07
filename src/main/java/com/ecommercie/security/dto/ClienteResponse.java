package com.ecommercie.security.dto;

import com.ecommercie.security.models.User;

/**
 * Cliente visto pelo admin (GET /admin/customers). Diferente do UsuarioResponse,
 * traz o id — usado em /admin/customers/{userId}/orders.
 */
public record ClienteResponse(
        String id,
        String nome,
        String email
) {

    public static ClienteResponse from(User user) {
        return new ClienteResponse(user.getId(), user.getNome(), user.getEmail());
    }
}
