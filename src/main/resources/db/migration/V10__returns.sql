ALTER TABLE pedido
    ADD COLUMN mp_payment_id      BIGINT,
    ADD COLUMN return_reason      VARCHAR(500),
    ADD COLUMN return_requested_at TIMESTAMP,
    ADD COLUMN refunded_at        TIMESTAMP;
