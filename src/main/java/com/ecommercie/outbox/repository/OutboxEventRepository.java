package com.ecommercie.outbox.repository;

import com.ecommercie.outbox.models.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Query(
            value = """
                    SELECT * FROM outbox_event
                    WHERE status = 'PENDING' AND next_attempt_at <= now()
                    ORDER BY created_at
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
                    nativeQuery = true
    )
    List<OutboxEvent> lockBatchForProcessing(@Param("batchSize") int batchSize);
}
