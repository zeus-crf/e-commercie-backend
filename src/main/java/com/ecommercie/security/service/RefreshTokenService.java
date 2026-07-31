package com.ecommercie.security.service;

import com.ecommercie.security.models.RefreshToken;
import com.ecommercie.security.repository.RefreshTokenRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;


    public RefreshToken validar(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Token não encontrado"));

        if (refreshToken.isRevogado()){
            throw new IllegalArgumentException("Token revogado");
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token expirado");
        }

        return refreshToken;
    }

    @Transactional
    public void revogar(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token não encontrado"));

        if (!refreshToken.isRevogado()) {
            refreshToken.setRevogado(true);
            refreshTokenRepository.save(refreshToken);
        }
    }
}
