package com.ecommercie.pedido.repository;

import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.StatusOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    Page<Order> findByUserId(String userId, Pageable pageable);

    Optional<Order> findByIdAndUserId(String id, String userId);

    Page<Order> findByStatus(StatusOrder status, Pageable pageable);

    Page<Order> findByUserIdAndStatus(String userId, StatusOrder status, Pageable pageable);

    List<Order> findByStatusAndExpiresAtBefore(StatusOrder status, LocalDateTime agora);

    // trava a linha do pedido ate o fim da transacao (ex.: duas confirmacoes de devolucao ao mesmo tempo)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findWithLockById(String id);
}
