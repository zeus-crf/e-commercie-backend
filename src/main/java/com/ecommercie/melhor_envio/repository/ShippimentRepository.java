package com.ecommercie.melhor_envio.repository;

import com.ecommercie.melhor_envio.models.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ShippimentRepository extends JpaRepository<Shipment, String> {
    Optional<Shipment> findByMeOrderId(String meOrderId);
    Optional<Shipment> findByOrderId(String orderId);

    // Reserva a compra de um envio PENDENTE: so passa se ninguem reservou ou se a reserva expirou.
    // Atomico no Postgres: um UPDATE concorrente espera o lock da linha e reavalia o WHERE.
    @Modifying
    @Query("""
            UPDATE Shipment s SET s.checkoutIniciadoEm = :agora
            WHERE s.order.id = :orderId
              AND s.labelGeneratedAt IS NULL
              AND (s.checkoutIniciadoEm IS NULL OR s.checkoutIniciadoEm < :expiraAntesDe)
            """)
    int reservarCheckout(@Param("orderId") String orderId,
                         @Param("agora") LocalDateTime agora,
                         @Param("expiraAntesDe") LocalDateTime expiraAntesDe);

    // Apaga o envio PENDENTE do pedido (o ME recusou a compra): o proximo retry monta carrinho novo
    @Modifying
    @Query("DELETE FROM Shipment s WHERE s.order.id = :orderId AND s.labelGeneratedAt IS NULL")
    int descartarPendente(@Param("orderId") String orderId);
}
