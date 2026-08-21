package com.ecommercie.pedido.models;

import com.ecommercie.security.models.User;
import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

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

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "endereco_id", nullable = false)
    private Address address;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusOrder status = StatusOrder.AGUARDANDO_PAGAMENTO;

    @Column(nullable = false, name = "valor_itens")
    private BigDecimal valorItens;

    @Column(nullable = false, name = "valor_frete")
    private BigDecimal valorFrete;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> itens = new ArrayList<>();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "mp_payment_id")
    private Long mpPaymentId;

    @Column(name = "return_reason", length = 500)
    private String returnReason;

    @Column(name = "return_requested_at")
    private LocalDateTime returnRequestedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    
    public void markPaid() {
        if (this.status != StatusOrder.AGUARDANDO_PAGAMENTO) {
            throw new IllegalArgumentException("Só um pedido aguardando pagamento pode ser pago");
        }
        this.status = StatusOrder.PAGO;
    }

    public void markCancelado() {
        if (this.status != StatusOrder.AGUARDANDO_PAGAMENTO) {
            throw new IllegalArgumentException("Só um pedido aguardando pagamento pode ser cancelado");
        }
        this.status = StatusOrder.CANCELADO;
    }

    public void markSeparando() {
        if (this.status != StatusOrder.PAGO) {
            throw new IllegalArgumentException("Só um pedido pago pode ser separado");
        }
        this.status = StatusOrder.EM_SEPARACAO;
    }

    public void markEnviando() {
        if (this.status != StatusOrder.EM_SEPARACAO) {
            throw new IllegalArgumentException("Só um pedido separado pode ser enviado");
        }
        this.status = StatusOrder.ENVIADO;
    }

    public void markEntregue() {
        if (this.status != StatusOrder.ENVIADO) {
            throw new IllegalArgumentException("Só um pedido enviado pode ser entregue");
        }
        this.status = StatusOrder.ENTREGUE;
    }


    public void markDevolucaoSolicitada() {
        if (this.status != StatusOrder.PAGO &&
            this.status != StatusOrder.EM_SEPARACAO &&
            this.status != StatusOrder.ENVIADO &&
            this.status != StatusOrder.ENTREGUE) {
            throw new IllegalArgumentException("Devolução só pode ser solicitada para pedidos pagos, em separação, enviados ou entregues");
        }
        this.status = StatusOrder.DEVOLUCAO_SOLICITADA;
    }

    public void markDevolucao() {
        if (this.status != StatusOrder.DEVOLUCAO_SOLICITADA) {
            throw new IllegalArgumentException("Só um pedido que teve uma devolução solicitada pode ser devolvido");
        }
        this.status = StatusOrder.DEVOLVIDO;
    }



}
