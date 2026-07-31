package com.ecommercie.security.repository;

import com.ecommercie.security.models.RefreshToken;
import com.ecommercie.security.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findAllByUsuario(User usuario);
}
