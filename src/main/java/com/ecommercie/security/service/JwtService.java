package com.ecommercie.security.service;

import com.ecommercie.security.models.RefreshToken;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${security.jwt.secret}")
    private String secretKey;

    @Value("${security.jwt.access-expiration}")
    private long accessExpiration;

    @Value("${security.jwt.refresh-expiration}")
    private long refreshExpiration;

    public String generateAccessToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(getSignKey())
                .compact();
    }

    @Transactional
    public String generateRefreshToken(User usuario) {

        List<RefreshToken> listRefresh = refreshTokenRepository.findAllByUsuario(usuario);

        listRefresh.forEach(l -> l.setRevogado(true));

        refreshTokenRepository.saveAll(listRefresh);

        var refreshToken = UUID.randomUUID().toString();

        RefreshToken refresh = RefreshToken.builder()
                        .token(refreshToken)
                                .usuario(usuario)
                                        .expiresAt(LocalDateTime.now().plus(refreshExpiration, ChronoUnit.MILLIS))
                                                .revogado(false)
                                                        .build();

        refreshTokenRepository.save(refresh);

        return refreshToken;
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(Jwts.parser().verifyWith(getSignKey()).build()
                .parseSignedClaims(token).getPayload());
    }

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}