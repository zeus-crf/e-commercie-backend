package com.ecommercie.security.controller;

import com.ecommercie.security.dto.AuthRequest;
import com.ecommercie.security.dto.RegisterRequestUser;
import com.ecommercie.security.dto.UsuarioResponse;
import com.ecommercie.security.service.AuthService;
import com.ecommercie.shared.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UsuarioResponse>> login (@Valid @RequestBody AuthRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok("Login realizado com sucesso! ", authService.login(request, response)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UsuarioResponse>> registerCliente (@Valid @RequestBody RegisterRequestUser request, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok("Conta criada com sucesso!", authService.registerCliente(request, response)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<UsuarioResponse>> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken, HttpServletResponse response) {

        if (refreshToken == null) {
            throw new IllegalArgumentException("Refresh Token ausente");
        }

        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(refreshToken, response)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken, response);
        return ResponseEntity.ok(ApiResponse.ok("Logout realizado com sucesso", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UsuarioResponse>> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(userDetails)));
    }




}
