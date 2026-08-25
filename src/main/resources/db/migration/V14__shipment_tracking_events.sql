CREATE TABLE shipment_tracking_events (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    shipment_id   VARCHAR(36)  NOT NULL REFERENCES envios(id),
    status        VARCHAR(60)  NOT NULL,
    tracking_code VARCHAR(60),
    occurred_at   TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);
