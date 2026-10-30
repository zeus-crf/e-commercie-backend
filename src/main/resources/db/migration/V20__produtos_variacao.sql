CREATE TABLE produto_variacao (
id VARCHAR(36) NOT NULL PRIMARY KEY,
product_id VARCHAR(36) NOT NULL,
sku VARCHAR(64),
tamanho VARCHAR(20),
cor VARCHAR(40),
preco NUMERIC(12,2),
peso_kg NUMERIC(10, 3),
altura_cm NUMERIC(10,3),
largura_cm NUMERIC(10,3),
comprimento_cm NUMERIC(10, 3),
ativo BOOLEAN NOT NULL DEFAULT TRUE,
created_at TIMESTAMP NOT NULL,
updated_at TIMESTAMP,
CONSTRAINT fk_variacao_produto FOREIGN KEY (product_id) REFERENCES produtos (id),
CONSTRAINT uq_variacao_sku UNIQUE (sku),

 -- NULLS NOT DISTINCT: so pode existir UMA variacao "unica" (tamanho e cor nulos) por produto
    CONSTRAINT uq_variacao_tamanho_cor UNIQUE NULLS NOT DISTINCT (product_id, tamanho, cor)
);

INSERT INTO produto_variacao (id, product_id, ativo, created_at)
SELECT gen_random_uuid()::text, p.id, TRUE, now()
FROM produtos p;