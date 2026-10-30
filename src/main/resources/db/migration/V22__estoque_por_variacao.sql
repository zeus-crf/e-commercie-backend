-- Cada variacao tem o proprio estoque. O estoque atual do produto vai para a variacao unica dele.
ALTER TABLE inventory_item ADD COLUMN variant_id VARCHAR(36);
UPDATE inventory_item i SET variant_id = (
    SELECT v.id FROM produto_variacao v WHERE v.product_id = i.product_id ORDER BY v.created_at LIMIT 1);
ALTER TABLE inventory_item ALTER COLUMN variant_id SET NOT NULL;
ALTER TABLE inventory_item ADD CONSTRAINT uq_inventory_variacao UNIQUE (variant_id);
ALTER TABLE inventory_item ADD CONSTRAINT fk_inventory_variacao
    FOREIGN KEY (variant_id) REFERENCES produto_variacao (id);
ALTER TABLE inventory_item DROP COLUMN product_id;   -- leva junto a UNIQUE e a fk_inventory_produto