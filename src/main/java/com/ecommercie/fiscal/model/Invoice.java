package com.ecommercie.fiscal.model;

import com.ecommercie.fiscal.enums.FiscalDocumentType;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "invoice")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiscalDocumentType tipo;

    @Column(nullable = false)
    private String chave;

    @Column(nullable = false)
    private String status;
}
