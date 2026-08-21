package com.ecommercie.devolucao.controller;

import com.ecommercie.devolucao.service.ReturnService;
import com.ecommercie.security.models.User;
import com.ecommercie.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    public record DevolucaoRequest(String motivo) {}

    @PostMapping("/{orderId}/return")
    public ResponseEntity<ApiResponse<Void>> solicitarDevolucao(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable String orderId,
                                                                @RequestBody DevolucaoRequest body) {
        returnService.devolucaoSolicitada((User) userDetails, orderId, body.motivo());
        return ResponseEntity.ok(ApiResponse.ok("Devolução solicitada com sucesso", null));
    }
}
