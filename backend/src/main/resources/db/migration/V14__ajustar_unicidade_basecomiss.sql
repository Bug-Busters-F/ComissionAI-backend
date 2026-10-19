-- =============================================================================
-- V14: Ajuste de unicidade em tb_basecomiss
-- =============================================================================
-- Remove eventuais duplicatas mantendo apenas o primeiro registro por
-- (brand_id, position_id, reference_month), e cria a restrição de unicidade.
-- =============================================================================

DELETE FROM tb_basecomiss
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY brand_id, position_id, reference_month ORDER BY id) AS rn
        FROM tb_basecomiss
    ) sub
    WHERE sub.rn > 1
);

ALTER TABLE tb_basecomiss
ADD CONSTRAINT uk_basecomiss_brand_position_month UNIQUE (brand_id, position_id, reference_month);
