ALTER TABLE envios RENAME COLUMN label_generated_at TO etiqueta_paga_em;
ALTER TABLE envios ADD COLUMN etiqueta_gerada_em TIMESTAMP;