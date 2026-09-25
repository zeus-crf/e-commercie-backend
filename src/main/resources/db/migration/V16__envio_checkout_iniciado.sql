-- Reserva do checkout da etiqueta no Melhor Envio: so a requisicao que reservou pode pagar o item.
-- NULL = ninguem reservou. Uma reserva mais antiga que a janela (5 min) pode ser retomada.
ALTER TABLE envios ADD COLUMN checkout_iniciado_em TIMESTAMP;
