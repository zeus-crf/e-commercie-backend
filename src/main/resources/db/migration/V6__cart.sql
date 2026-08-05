-- V6__cart.sql
-- Ordem importa por causa das FKs: carrinho -> carrinho_item

CREATE TABLE carrinho (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL UNIQUE,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_carrinho_usuario FOREIGN KEY (user_id) REFERENCES usuarios (id)
);

CREATE TABLE carrinho_item (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    cart_id    VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    quantidade INTEGER     NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_item_carrinho FOREIGN KEY (cart_id) REFERENCES carrinho (id),
    CONSTRAINT fk_item_produto  FOREIGN KEY (product_id) REFERENCES produtos (id)
);
