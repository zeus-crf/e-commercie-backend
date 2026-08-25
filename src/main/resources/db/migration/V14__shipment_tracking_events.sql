CREATE TABLE shipment_tracking_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID        NOT NULL REFERENCES envios(id),
    status      VARCHAR(60) NOT NULL,
    tracking_code VARCHAR(60),
    occurred_at TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);
