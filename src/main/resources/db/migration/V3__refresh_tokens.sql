CREATE TABLE refresh_tokens (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    token      VARCHAR(512) NOT NULL UNIQUE,
    usuario_id VARCHAR(36)  NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revogado   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);
