CREATE TABLE envios (
    id                  VARCHAR(36)    NOT NULL PRIMARY KEY,
    pedido_id           VARCHAR(36)    NOT NULL,
    me_order_id         VARCHAR(100),
    me_protocol         VARCHAR(100),
    service_id          INTEGER,
    tracking_code       VARCHAR(100),
    tracking_status     VARCHAR(50),
    price               NUMERIC(12,2),
    label_generated_at  TIMESTAMP,
    posted_at           TIMESTAMP,
    delivered_at        TIMESTAMP,
    created_at          TIMESTAMP      NOT NULL,
    updated_at          TIMESTAMP,
    CONSTRAINT fk_envio_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id),
    CONSTRAINT uq_envio_pedido UNIQUE (pedido_id)
);
