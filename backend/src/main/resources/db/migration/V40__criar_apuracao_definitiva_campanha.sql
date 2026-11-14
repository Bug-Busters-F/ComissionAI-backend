-- =============================================================================
-- V32: Apuração Definitiva por Campanha no Motor Atual (BUG-109 / BACK-14)
-- =============================================================================
-- Registra as apurações financeiras definitivas executadas por campanha,
-- vinculando o programa Python homologado, os resultados congelados em
-- tb_resultado_calculo e os logs de auditoria imutável.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tb_campanha_apuracao (
    id UUID PRIMARY KEY,
    campanha_id BIGINT NOT NULL REFERENCES tb_campanha(id) ON DELETE CASCADE,
    campanha_versao_id BIGINT REFERENCES tb_campanha_versao(id) ON DELETE CASCADE,
    protocolo UUID NOT NULL UNIQUE,
    competencia VARCHAR(7),
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    versao_campanha_numero INTEGER NOT NULL,
    versao_programa_python INTEGER,
    checksum_python VARCHAR(64),
    executado_via_python BOOLEAN NOT NULL DEFAULT FALSE,
    total_vendas_processadas INTEGER NOT NULL DEFAULT 0,
    total_calculados INTEGER NOT NULL DEFAULT 0,
    total_impedimentos INTEGER NOT NULL DEFAULT 0,
    valor_total_vendas NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    valor_total_comissao NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    valor_total_bonus NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    executado_por VARCHAR(150) NOT NULL,
    executado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'CONCLUIDO',
    observacoes TEXT
);

-- Vínculo dos resultados de cálculo com a apuração e campanha
ALTER TABLE tb_resultado_calculo ADD COLUMN IF NOT EXISTS campanha_id BIGINT REFERENCES tb_campanha(id) ON DELETE SET NULL;
ALTER TABLE tb_resultado_calculo ADD COLUMN IF NOT EXISTS versao_programa_python INTEGER;
ALTER TABLE tb_resultado_calculo ADD COLUMN IF NOT EXISTS checksum_python VARCHAR(64);
ALTER TABLE tb_resultado_calculo ADD COLUMN IF NOT EXISTS apuracao_campanha_id UUID REFERENCES tb_campanha_apuracao(id) ON DELETE SET NULL;

-- Índices para buscas rápidas de apuração e resultados
CREATE INDEX IF NOT EXISTS idx_campanha_apuracao_campanha_id ON tb_campanha_apuracao(campanha_id);
CREATE INDEX IF NOT EXISTS idx_campanha_apuracao_competencia ON tb_campanha_apuracao(competencia);
CREATE INDEX IF NOT EXISTS idx_resultado_calculo_campanha_id ON tb_resultado_calculo(campanha_id);
CREATE INDEX IF NOT EXISTS idx_resultado_calculo_apuracao_id ON tb_resultado_calculo(apuracao_campanha_id);

