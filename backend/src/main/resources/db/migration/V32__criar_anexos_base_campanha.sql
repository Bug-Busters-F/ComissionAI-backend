-- =============================================================================
-- V32: Anexos de Bases Gerais por Campanha para Cobertura de Fechamento (BACK-13)
-- =============================================================================

CREATE TABLE IF NOT EXISTS tb_campanha_anexo_base (
    id UUID PRIMARY KEY,
    campanha_id BIGINT NOT NULL REFERENCES tb_campanha(id) ON DELETE RESTRICT,
    envio_arquivo_id BIGINT NOT NULL REFERENCES tb_envio_arquivo(id) ON DELETE RESTRICT,
    tipo_base VARCHAR(30) NOT NULL,
    competencia VARCHAR(7),
    anexado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    anexado_por VARCHAR(150) NOT NULL DEFAULT 'SISTEMA',
    desanexado_em TIMESTAMP WITH TIME ZONE,
    desanexado_por VARCHAR(150),
    CONSTRAINT chk_campanha_anexo_tipo_base
        CHECK (tipo_base IN ('HR', 'SALES', 'COMISSIONS')),
    CONSTRAINT chk_campanha_anexo_competencia
        CHECK (
            (tipo_base = 'HR' AND competencia IS NULL)
            OR (tipo_base IN ('SALES', 'COMISSIONS') AND competencia IS NOT NULL)
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_campanha_anexo_ativo
    ON tb_campanha_anexo_base(campanha_id, envio_arquivo_id)
    WHERE desanexado_em IS NULL;

CREATE INDEX IF NOT EXISTS idx_campanha_anexo_campanha_ativo
    ON tb_campanha_anexo_base(campanha_id)
    WHERE desanexado_em IS NULL;

CREATE INDEX IF NOT EXISTS idx_campanha_anexo_envio_ativo
    ON tb_campanha_anexo_base(envio_arquivo_id)
    WHERE desanexado_em IS NULL;

CREATE INDEX IF NOT EXISTS idx_campanha_anexo_campanha_comp_tipo
    ON tb_campanha_anexo_base(campanha_id, competencia, tipo_base)
    WHERE desanexado_em IS NULL;
