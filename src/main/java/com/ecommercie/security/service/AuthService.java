package com.ecommercie.security.service;

import com.ecommercie.security.dto.AuthRequest;
import com.ecommercie.security.dto.UsuarioResponse;
import com.ecommercie.security.models.RefreshToken;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UsuarioDetailsService usuarioDetailsService;
    private  final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    @Value("${security.jwt.access-expiration}")
    private long accessExpiration;

    @Value("${security.jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${security.cookie.secure:true}")
    private boolean cookieSecure;


    public UsuarioResponse login(@Valid AuthRequest request, HttpServletResponse response) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.senha())
        );

        User usuario = (User) usuarioDetailsService.loadUserByUsername(request.email());

        var accessToken = jwtService.generateAccessToken(usuario);
        var refreshToken = jwtService.generateRefreshToken(usuario);


        setarCookies(response, accessToken, refreshToken);

        return new UsuarioResponse(usuario.getNome(), usuario.getEmail());
    }


    private String sameSitePolicy() {
        // SameSite=None exige Secure=true (HTTPS). Em dev (secure=false) usamos Lax.
        return cookieSecure ? "None" : "Lax";
    }


    private void setarCookies(HttpServletResponse response, String accessToken, String refreshToken) {

        ResponseCookie access = ResponseCookie.from("access_token", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(accessExpiration / 1000)
                .sameSite(sameSitePolicy())
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, access.toString());


        ResponseCookie refresh = ResponseCookie.from("refresh_token", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(accessExpiration / 1000)
                .sameSite(sameSitePolicy())
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());

    }

    private void limparCookies(HttpServletResponse response) {

        ResponseCookie access = ResponseCookie.from("access_token")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite(sameSitePolicy())
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, access.toString());

        ResponseCookie refresh = ResponseCookie.from("refresh_token")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(0)
                .sameSite(sameSitePolicy())
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
    }



}
