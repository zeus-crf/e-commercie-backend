package com.ecommercie.security.models;


import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

    @Column(unique = true)
    private String token;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean revogado = false;
}
