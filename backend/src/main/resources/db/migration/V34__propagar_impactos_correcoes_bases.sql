-- =============================================================================
-- V34: Propagação de Impactos de Correções de Bases Globais (BACK-6)
-- =============================================================================

-- 1. Controle de desatualização por correção posterior e encadeamento em tb_resultado_calculo
ALTER TABLE tb_resultado_calculo
    ADD COLUMN IF NOT EXISTS desatualizado_por_correcao BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS desatualizado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS motivo_desatualizacao TEXT,
    ADD COLUMN IF NOT EXISTS lote_correcao_id UUID REFERENCES tb_lote_preparacao(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS protocolo_anterior UUID;

CREATE INDEX IF NOT EXISTS idx_resultado_desatualizado ON tb_resultado_calculo(desatualizado_por_correcao);
CREATE INDEX IF NOT EXISTS idx_resultado_lote_correcao ON tb_resultado_calculo(lote_correcao_id);

-- 2. Tabela de Preservação Imutável de Snapshots de Resultados de Cálculo
CREATE TABLE IF NOT EXISTS tb_snapshot_resultado_calculo (
    id UUID PRIMARY KEY,
    resultado_calculo_id BIGINT NOT NULL,
    protocolo_calculo UUID NOT NULL,
    id_venda UUID NOT NULL,
    matricula VARCHAR(50) NOT NULL,
    cod_marca INT,
    cod_loja INT,
    cod_cargo INT,
    regra_id BIGINT NOT NULL,
    origem_taxa VARCHAR(30),
    data_venda DATE NOT NULL,
    valor_venda NUMERIC(15, 2) NOT NULL,
    taxa_aplicada NUMERIC(6, 4) NOT NULL,
    valor_comissao NUMERIC(15, 2) NOT NULL,
    valor_bonus_percentual NUMERIC(15, 2) DEFAULT 0.00,
    valor_bonus_fixo NUMERIC(15, 2) DEFAULT 0.00,
    versao_campanha_numero INT,
    fechamento_id UUID,
    tipo_calculo VARCHAR(50),
    calculado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    preservado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo_preservacao TEXT,
    lote_preparacao_id UUID REFERENCES tb_lote_preparacao(id) ON DELETE SET NULL,
    substituido_por_protocolo UUID
);

CREATE INDEX IF NOT EXISTS idx_snapshot_id_venda ON tb_snapshot_resultado_calculo (id_venda);
CREATE INDEX IF NOT EXISTS idx_snapshot_protocolo ON tb_snapshot_resultado_calculo (protocolo_calculo);
CREATE INDEX IF NOT EXISTS idx_snapshot_lote ON tb_snapshot_resultado_calculo (lote_preparacao_id);

-- 3. Campos de aprovação e invalidação de código em tb_regra e tb_campanha_versao
ALTER TABLE tb_regra
    ADD COLUMN IF NOT EXISTS status_aprovacao_codigo VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    ADD COLUMN IF NOT EXISTS hash_artefatos_codigo VARCHAR(64),
    ADD COLUMN IF NOT EXISTS codigo_aprovado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS codigo_aprovado_por VARCHAR(150),
    ADD COLUMN IF NOT EXISTS codigo_invalidado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS motivo_invalidacao_codigo TEXT;

ALTER TABLE tb_campanha_versao
    ADD COLUMN IF NOT EXISTS status_aprovacao_codigo VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    ADD COLUMN IF NOT EXISTS hash_artefatos_codigo VARCHAR(64),
    ADD COLUMN IF NOT EXISTS codigo_aprovado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS codigo_aprovado_por VARCHAR(150),
    ADD COLUMN IF NOT EXISTS codigo_invalidado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS motivo_invalidacao_codigo TEXT;

CREATE INDEX IF NOT EXISTS idx_regra_aprovacao_codigo ON tb_regra (status_aprovacao_codigo);
CREATE INDEX IF NOT EXISTS idx_campanha_versao_aprovacao_codigo ON tb_campanha_versao (status_aprovacao_codigo);

-- 4. Tabela de Auditoria de Propagação de Impacto de Correções
CREATE TABLE IF NOT EXISTS tb_propagacao_impacto_base (
    id UUID PRIMARY KEY,
    lote_preparacao_id UUID NOT NULL REFERENCES tb_lote_preparacao(id) ON DELETE CASCADE,
    campanha_origem_id BIGINT NOT NULL REFERENCES tb_campanha(id) ON DELETE CASCADE,
    tipo_base VARCHAR(20) NOT NULL,
    competencias_afetadas VARCHAR(255),
    qtd_campanhas_afetadas INT NOT NULL DEFAULT 0,
    qtd_regras_afetadas INT NOT NULL DEFAULT 0,
    qtd_simulacoes_invalidadas INT NOT NULL DEFAULT 0,
    qtd_calculos_desatualizados INT NOT NULL DEFAULT 0,
    qtd_aprovacoes_codigo_invalidadas INT NOT NULL DEFAULT 0,
    detalhes_impacto_json TEXT,
    confirmado_por VARCHAR(150) NOT NULL,
    confirmado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo_confirmacao TEXT
);

CREATE INDEX IF NOT EXISTS idx_propagacao_lote ON tb_propagacao_impacto_base (lote_preparacao_id);
CREATE INDEX IF NOT EXISTS idx_propagacao_campanha ON tb_propagacao_impacto_base (campanha_origem_id);
