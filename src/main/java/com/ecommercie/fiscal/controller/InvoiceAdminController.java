package com.ecommercie.fiscal.controller;

import com.ecommercie.fiscal.dto.InvoiceResponse;
import com.ecommercie.fiscal.service.FiscalService;
import com.ecommercie.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders/{orderId}/invoice")
@RequiredArgsConstructor
public class InvoiceAdminController {

    private final FiscalService fiscalService;


    @GetMapping
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(@PathVariable String orderId){
        return fiscalService.buscarPorPedido(orderId)
                .map(inv ->
                        ResponseEntity.ok(ApiResponse.ok(InvoiceResponse.from(inv))))
                .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }
}
