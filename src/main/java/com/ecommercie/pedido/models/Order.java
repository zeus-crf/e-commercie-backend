package com.ecommercie.pedido.models;

import com.ecommercie.security.models.User;
import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Order extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "endereco_id", nullable = false)
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusOrder status;

    @Column(nullable = false, name = "valor_itens")
    private BigDecimal valorItens;

    @Column(nullable = false, name = "valor_frete")
    private BigDecimal valorFrete;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> itens = new ArrayList<>();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

}
