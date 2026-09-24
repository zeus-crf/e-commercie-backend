package com.ecommercie.security.controller;

import com.ecommercie.config.OpenApiConfig;
import com.ecommercie.security.dto.AuthRequest;
import com.ecommercie.security.dto.RegisterRequestUser;
import com.ecommercie.security.dto.UsuarioResponse;
import com.ecommercie.security.service.AuthService;
import com.ecommercie.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Autenticação", description = "Login, cadastro e sessão do usuário. Tokens trafegam em cookies httpOnly")
public class AuthController {

    private final AuthService authService;


    @Operation(summary = "Autentica o usuário com e-mail e senha",
            description = "Seta os cookies httpOnly access_token e refresh_token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UsuarioResponse>> login (@Valid @RequestBody AuthRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok("Login realizado com sucesso! ", authService.login(request, response)));
    }

    @Operation(summary = "Cadastra um novo cliente e já autentica")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UsuarioResponse>> registerCliente (@Valid @RequestBody RegisterRequestUser request, HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.ok("Conta criada com sucesso!", authService.registerCliente(request, response)));
    }

    @Operation(summary = "Renova o access token a partir do cookie refresh_token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<UsuarioResponse>> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken, HttpServletResponse response) {

        if (refreshToken == null) {
            throw new IllegalArgumentException("Refresh Token ausente");
        }

        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(refreshToken, response)));
    }

    @Operation(summary = "Encerra a sessão e limpa os cookies")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken, response);
        return ResponseEntity.ok(ApiResponse.ok("Logout realizado com sucesso", null));
    }

    @Operation(summary = "Exibe os dados do usuário logado")
    @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UsuarioResponse>> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(userDetails)));
    }




}
