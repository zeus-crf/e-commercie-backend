package com.ecommercie.devolucao.controller;

import com.ecommercie.config.OpenApiConfig;
import com.ecommercie.devolucao.service.ReturnService;
import com.ecommercie.shared.ApiResponse;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Devoluções do Administrador", description = "Onde o Administrador confirma devoluções e processa o reembolso")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ReturnAdminController {

    private final ReturnService returnService;

    @Operation(summary = "Confirma a devolução e estorna o pagamento no Mercado Pago")
    @PatchMapping("/{orderId}/return/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmarDevolucao(@PathVariable String orderId) throws MPException, MPApiException {
        returnService.confirmarDevolucao(orderId);
        return ResponseEntity.ok(ApiResponse.ok("Devolução confirmada e reembolso processado", null));
    }
}
