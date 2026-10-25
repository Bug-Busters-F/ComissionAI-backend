-- =============================================================================
-- V16: Índice para verificação de duplicidade de arquivo por hash, tipo e competência
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_envio_arquivo_hash_tipo_comp
    ON tb_envio_arquivo (hash_conteudo, tipo_base, competencia);
