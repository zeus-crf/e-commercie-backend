package com.ecommercie.outbox.relay;

import com.ecommercie.outbox.dispatcher.OutboxDispatcher;
import com.ecommercie.outbox.models.OutboxEvent;
import com.ecommercie.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Relay do outbox: lê um lote de eventos PENDING vencidos (com FOR UPDATE SKIP LOCKED),
 * despacha cada um (envio síncrono) e marca SENT; se falhar, reagenda com backoff.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxDispatcher outboxDispatcher;

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BACKOFF = Duration.ofMinutes(1);

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutbox() {
        var lote = outboxEventRepository.lockBatchForProcessing(BATCH_SIZE);

        for (OutboxEvent event : lote) {
            try {
                outboxDispatcher.dispatch(event);
                event.markSent();
            } catch (Exception e) {
                log.warn("Falha no outbox {} (tentativa {}): {}",
                        event.getId(), event.getAttempts() + 1, e.getMessage());
                event.markFailed(MAX_ATTEMPTS, BACKOFF);
            }
        }
    }
}
