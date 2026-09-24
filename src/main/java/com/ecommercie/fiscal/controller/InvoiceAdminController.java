package com.ecommercie.fiscal.controller;

import com.ecommercie.config.OpenApiConfig;
import com.ecommercie.fiscal.dto.InvoiceResponse;
import com.ecommercie.fiscal.service.FiscalService;
import com.ecommercie.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders/{orderId}/invoice")
@RequiredArgsConstructor
@Tag(name = "Fiscal do Administrador", description = "Onde o Administrador consulta os documentos fiscais dos pedidos")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class InvoiceAdminController {

    private final FiscalService fiscalService;


    @Operation(summary = "Exibe o documento fiscal (NF-e / DC-e) emitido para o pedido")
    @GetMapping
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(@PathVariable String orderId){
        return fiscalService.buscarPorPedido(orderId)
                .map(inv ->
                        ResponseEntity.ok(ApiResponse.ok(InvoiceResponse.from(inv))))
                .orElseThrow(() ->
                        new EntityNotFoundException("Nenhum documento fiscal emitido para o pedido: " + orderId));
    }
}
