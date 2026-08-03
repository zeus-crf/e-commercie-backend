-- V4__catalog.sql
-- Ordem importa por causa das FKs: categorias -> produtos -> produto_imagens

CREATE TABLE categorias (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    nome       VARCHAR(120) NOT NULL,
    slug       VARCHAR(150) UNIQUE,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE produtos (
    id             VARCHAR(36)   NOT NULL PRIMARY KEY,
    nome           VARCHAR(150)  NOT NULL,
    descricao      TEXT,
    preco          NUMERIC(12,2) NOT NULL,
    ativo          BOOLEAN       NOT NULL DEFAULT TRUE,
    category_id    VARCHAR(36)   NOT NULL,
    peso_kg        NUMERIC(10,3) NOT NULL,
    altura_cm      NUMERIC(10,3) NOT NULL,
    largura_cm     NUMERIC(10,3) NOT NULL,
    comprimento_cm NUMERIC(10,3) NOT NULL,
    ncm            VARCHAR(8),
    cfop           VARCHAR(4),
    origem         VARCHAR(1),
    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP,
    CONSTRAINT fk_produtos_categoria FOREIGN KEY (category_id) REFERENCES categorias (id)
);

CREATE TABLE produto_imagens (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    product_id VARCHAR(36)  NOT NULL,
    url        VARCHAR(500),
    ordem      INTEGER      NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_produto_imagens_produto FOREIGN KEY (product_id) REFERENCES produtos (id)
);
