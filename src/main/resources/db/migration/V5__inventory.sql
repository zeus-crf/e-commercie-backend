CREATE TABLE inventory_item (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL UNIQUE,
    disponivel INTEGER NOT NULL DEFAULT 0,
    reservada INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_inventory_produto FOREIGN KEY (product_id) REFERENCES produtos(id)
);