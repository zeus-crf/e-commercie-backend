package com.ecommercie.mercado_pago.controller;

import com.ecommercie.mercado_pago.dtos.RequestPreference;
import com.ecommercie.mercado_pago.dtos.ResponsePreference;
import com.ecommercie.shared.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoController {

    public ResponseEntity<ApiResponse<ResponsePreference>> createPreference(@Valid @RequestBody RequestPreference request){
        try {

        } catch (Exception ex) {

        }
    }

}
