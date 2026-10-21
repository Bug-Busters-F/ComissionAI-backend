-- =============================================================================
-- V16: Estruturas de Aceite, Invalidação e Ativação Vinculada à Simulação (BACK-8)
-- =============================================================================
-- Registra o aceite formal de simulações sem impedimentos, o congelamento do
-- fingerprint da proposta simulada e o vínculo de integridade com a campanha.
-- =============================================================================

-- 1. Campos de controle de aceite e auditoria na tabela de simulações
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS status_aceite VARCHAR(30) DEFAULT 'PENDENTE' NOT NULL;
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS aceito_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS aceito_por VARCHAR(150);
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS motivo_aceite TEXT;
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS hash_proposta VARCHAR(64);
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS invalidado_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS motivo_invalidacao TEXT;

-- 2. Vínculo de integridade na tabela de campanhas com a simulação aceita vigente
ALTER TABLE tb_campanha ADD COLUMN IF NOT EXISTS simulacao_aceita_id UUID REFERENCES tb_simulacao(id) ON DELETE SET NULL;

-- 3. Índices de desempenho para conferência e consulta rápida de aceite
CREATE INDEX IF NOT EXISTS idx_simulacao_campanha_aceite ON tb_simulacao (campanha_id, status_aceite);
CREATE INDEX IF NOT EXISTS idx_campanha_simulacao_aceita ON tb_campanha (simulacao_aceita_id);
