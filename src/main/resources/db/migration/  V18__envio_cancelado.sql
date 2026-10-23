-- Etiqueta cancelada nao e apagada: vira historico. A regra "um envio por pedido" passa a valer
-- so para o envio ATIVO (nao cancelado), para o pedido poder receber uma etiqueta nova.
ALTER TABLE envios ADD COLUMN etiqueta_cancelada_em TIMESTAMP;
ALTER TABLE envios ADD COLUMN cancelamento_motivo VARCHAR(255);
CREATE UNIQUE INDEX uq_envio_ativo_pedido ON envios (pedido_id) WHERE etiqueta_cancelada_em iS NULL;