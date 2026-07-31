CREATE TABLE usuarios (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    nome        VARCHAR(100) NOT NULL,
    email       VARCHAR(100) NOT NULL UNIQUE,
    senha       VARCHAR(100) NOT NULL,
    papel       VARCHAR(10)  NOT NULL,
    cpf_cnpj    VARCHAR(20)  NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP
);
