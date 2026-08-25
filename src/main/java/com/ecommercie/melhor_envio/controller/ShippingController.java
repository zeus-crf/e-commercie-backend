package com.ecommercie.melhor_envio.controller;

import com.ecommercie.melhor_envio.dto.ShippingQuote;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.ecommercie.melhor_envio.service.ShippingService;
import com.ecommercie.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<List<ShippingQuote>>> quote(@RequestBody ShippingQuoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(shippingService.quote(request)));
    }

}
