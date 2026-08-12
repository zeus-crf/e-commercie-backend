package com.ecommercie.outbox.models;

import com.ecommercie.outbox.enums.OutboxStatus;
import com.ecommercie.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent extends BaseEntity {

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;


    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt = LocalDateTime.now();


    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static OutboxEvent of(String type, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.type = type;
        e.payload = payload;
        return e;
    }

    public void  markFailed(int maxAttempts, Duration backoff) {
        this.attempts++;
        if (this.attempts >= maxAttempts){
            this.status = OutboxStatus.FAILED;
        } else {
            this.nextAttemptAt = LocalDateTime.now().plus(backoff.multipliedBy(attempts));
        }
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.processedAt = LocalDateTime.now();
    }


}
