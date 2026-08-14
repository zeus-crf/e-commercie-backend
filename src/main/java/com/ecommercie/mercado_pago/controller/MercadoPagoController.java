package com.ecommercie.mercado_pago.controller;

import com.ecommercie.mercado_pago.dtos.PaymentPreference;
import com.ecommercie.mercado_pago.dtos.RequestPreference;
import com.ecommercie.mercado_pago.dtos.ResponsePreference;
import com.ecommercie.mercado_pago.service.PreferenceService;
import com.ecommercie.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoController {

    private final PreferenceService preferenceService;

    @PostMapping("/{orderId}/preference")
    public ResponseEntity<ApiResponse<ResponsePreference>> criarPreferencia(
            @Valid @RequestBody RequestPreference request,
            @PathVariable String orderId) {
        try {
            PaymentPreference pref = preferenceService.criarPreferencia(request, orderId);
            return ResponseEntity.ok(ApiResponse.ok(new ResponsePreference(pref.preferenceId(), pref.redirectUrl())));
        } catch (Exception ex) {
            log.error("Erro ao criar preferência: {}", ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
