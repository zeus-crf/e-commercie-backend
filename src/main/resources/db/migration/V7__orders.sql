CREATE TABLE endereco (
id          VARCHAR(36)     NOT NULL PRIMARY KEY,
logradouro  VARCHAR(200)    NOT NULL,
numero      VARCHAR(10)     NOT NULL,
bairro      VARCHAR(100)    NOT NULL,
cidade      VARCHAR(100)    NOT NULL,
uf          VARCHAR(20)     NOT NULL,
cpf         VARCHAR(50)     NOT NULL
);


CREATE TABLE pedido (
    id          VARCHAR(36)   NOT NULL PRIMARY KEY,
    usuario_id  VARCHAR(36)   NOT NULL,
    adress_id   VARCHAR(36)   NOT NULL,
    status      VARCHAR(30)   NOT NULL,
    valor_itens NUMERIC(12,2) NOT NULL,
    valor_frete NUMERIC(12,2) NOT NULL,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP     NOT NULL,
    updated_at  TIMESTAMP,
    CONSTRAINT fk_pedido_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_pedido_endereco FOREIGN KEY (adress_id) REFERENCES endereco (id)
);

CREATE TABLE pedido_item (
    id             VARCHAR(36)   NOT NULL PRIMARY KEY,
    pedido_id      VARCHAR(36)   NOT NULL,
    product_id     VARCHAR(36),
    nome_produto   VARCHAR(150)  NOT NULL,
    preco_unitario NUMERIC(12,2) NOT NULL,
    quantidade     INTEGER       NOT NULL,
    ncm            VARCHAR(8),
    cfop           VARCHAR(4),
    origem         VARCHAR(1),
    created_at     TIMESTAMP     NOT NULL,
    updated_at     TIMESTAMP,
    CONSTRAINT fk_item_pedido  FOREIGN KEY (pedido_id) REFERENCES pedido (id),
    CONSTRAINT fk_item_produto FOREIGN KEY (product_id) REFERENCES produtos (id)
);