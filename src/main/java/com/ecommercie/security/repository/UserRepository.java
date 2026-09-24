package com.ecommercie.security.repository;

import com.ecommercie.security.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);


    @Query("""
            SELECT u FROM User u
            WHERE u.papel = com.ecommercie.security.models.Papel.CLIENTE
            AND (CAST(:q AS string) IS NULL
                OR LOWER(u.nome) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<User> buscarCliente(@Param("q") String q, Pageable pageable);
}
