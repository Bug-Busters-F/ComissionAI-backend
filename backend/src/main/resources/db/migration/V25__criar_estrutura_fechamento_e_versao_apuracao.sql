-- =============================================================================
-- V25: Estruturas de Fechamento de Apuração Final e Vínculo à Versão Vigente (BACK-11)
-- =============================================================================
-- Permite persistir o fechamento final da competência com data, protocolo e totais.
-- Registra a versão da campanha vigente aplicada aos resultados de cálculo.
-- Garante o bloqueio de alterações em campanhas durante e após a apuração final.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tb_fechamento_competencia (
    id UUID PRIMARY KEY,
    competencia VARCHAR(7) NOT NULL,
    protocolo UUID NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    tipo VARCHAR(30) NOT NULL DEFAULT 'FINAL',
    total_vendas_processadas INTEGER NOT NULL DEFAULT 0,
    total_calculados INTEGER NOT NULL DEFAULT 0,
    total_impedimentos INTEGER NOT NULL DEFAULT 0,
    valor_total_vendas NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    valor_total_comissao NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    executado_por VARCHAR(150),
    motivo TEXT,
    iniciado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    concluido_em TIMESTAMP WITH TIME ZONE,
    motivo_falha TEXT
);

-- Rastreabilidade de versão e vínculo com o fechamento em tb_resultado_calculo
ALTER TABLE tb_resultado_calculo ADD COLUMN IF NOT EXISTS versao_campanha_numero INTEGER;
ALTER TABLE tb_resultado_calculo ADD COLUMN IF NOT EXISTS fechamento_id UUID REFERENCES tb_fechamento_competencia(id) ON DELETE SET NULL;

-- Índices de consulta rápida
CREATE INDEX IF NOT EXISTS idx_fechamento_competencia_status ON tb_fechamento_competencia(competencia, status);
CREATE INDEX IF NOT EXISTS idx_resultado_fechamento ON tb_resultado_calculo(fechamento_id);
CREATE INDEX IF NOT EXISTS idx_resultado_versao_campanha ON tb_resultado_calculo(versao_campanha_numero);

-- A failed attempt remains auditable; at most one active or completed closing can reserve a month.
CREATE UNIQUE INDEX uk_fechamento_competencia_reservada ON tb_fechamento_competencia(competencia)
    WHERE status IN ('PROCESSANDO', 'CONCLUIDO');
