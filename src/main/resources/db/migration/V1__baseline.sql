-- V1__baseline.sql
-- Baseline do schema — NAO cria tabelas.
--
-- Objetivo: inicializar o historico do Flyway (tabela flyway_schema_history) e
-- validar o pipeline "Postgres + Flyway + Spring" subindo juntos (Fase 1 do plano).
--
-- As tabelas de dominio comecam nas proximas migrations:
--   V2__users.sql, V3__refresh_tokens.sql  (Fase 2)
--   V4__catalog.sql                         (Fase 3)
--   ... ver docs/implementation-plan.md e o ER em docs/system-design.md (§3.1)
--
-- Convencoes deste projeto (Postgres):
--   - id            VARCHAR(36)   (UUID gerado na aplicacao, via BaseEntity)
--   - created_at    TIMESTAMP     NOT NULL
--   - updated_at    TIMESTAMP
--   - dinheiro      NUMERIC(12,2)
--   - payload cru   JSONB

SELECT 1;
