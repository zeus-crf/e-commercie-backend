package com.ecommercie.devolucao.controller;

import com.ecommercie.devolucao.service.ReturnService;
import com.ecommercie.shared.ApiResponse;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class ReturnAdminController {

    private final ReturnService returnService;

    @PatchMapping("/{orderId}/return/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmarDevolucao(@PathVariable String orderId) throws MPException, MPApiException {
        returnService.confirmarDevolucao(orderId);
        return ResponseEntity.ok(ApiResponse.ok("Devolução confirmada e reembolso processado", null));
    }
}
