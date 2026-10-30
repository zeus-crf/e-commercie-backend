-- Carrinho e pedido passam a apontar para a variacao. A V20 criou exatamente uma variacao por produto,
-- entao "a primeira variacao do produto" e a variacao unica.

-- rede de seguranca: produto criado entre a V20 e este deploy nao tem variacao; sem ela o SET NOT NULL abaixo
-- derrubaria a migration. Cria a variacao "unica" so para quem ainda nao tem nenhuma.
INSERT INTO produto_variacao (id, product_id, ativo, created_at)
SELECT gen_random_uuid()::text, p.id, TRUE, now()
FROM produtos p
WHERE NOT EXISTS (SELECT 1 FROM produto_variacao v WHERE v.product_id = p.id);

ALTER TABLE carrinho_item ADD COLUMN variant_id VARCHAR(36);
UPDATE carrinho_item ci SET variant_id = (
    SELECT v.id FROM produto_variacao v WHERE v.product_id = ci.product_id ORDER BY v.created_at LIMIT 1);
ALTER TABLE carrinho_item ALTER COLUMN variant_id SET NOT NULL;
ALTER TABLE carrinho_item ADD CONSTRAINT fk_carrinho_item_variacao
    FOREIGN KEY (variant_id) REFERENCES produto_variacao (id);
ALTER TABLE carrinho_item DROP COLUMN product_id;   -- leva junto a fk_item_produto do carrinho

-- o pedido mantem product_id (historico, nota) e ganha a variacao + retrato do tamanho/cor/sku
ALTER TABLE pedido_item
    ADD COLUMN variant_id VARCHAR(36),
    ADD COLUMN tamanho    VARCHAR(20),
    ADD COLUMN cor        VARCHAR(40),
    ADD COLUMN sku        VARCHAR(64);
UPDATE pedido_item pi SET variant_id = (
    SELECT v.id FROM produto_variacao v WHERE v.product_id = pi.product_id ORDER BY v.created_at LIMIT 1)
WHERE pi.product_id IS NOT NULL;
ALTER TABLE pedido_item ADD CONSTRAINT fk_pedido_item_variacao
    FOREIGN KEY (variant_id) REFERENCES produto_variacao (id);