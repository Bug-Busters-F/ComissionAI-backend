-- =============================================================================
-- V17: Adicionar severidade ADVERTENCIA em tb_severidade_inconsistencia
-- =============================================================================

INSERT INTO tb_severidade_inconsistencia (codigo, descricao) VALUES
    ('ADVERTENCIA', 'Advertência ou inconsistência parcial não impeditiva')
ON CONFLICT (codigo) DO NOTHING;
