-- =============================================================================
-- V12: Adaptação de regras para coleções por campanha, faixas de valor,
-- operações sobre a base, referências e artefatos explicativos
-- =============================================================================

-- 1. Permitir taxa nula para salvar propostas/regras incompletas como rascunho (DRAFT)
ALTER TABLE tb_regra ALTER COLUMN taxa DROP NOT NULL;

-- 2. Condições de valor monetário (limites inclusivos e exclusivos)
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS valor_minimo NUMERIC(15, 2);
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS min_inclusivo BOOLEAN;
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS valor_maximo NUMERIC(15, 2);
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS max_inclusivo BOOLEAN;

-- 3. Operações sobre a base de comissão
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS tipo_operacao VARCHAR(50) DEFAULT 'DEFINIR_TAXA';
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS valor_ajuste NUMERIC(8, 4);

-- 4. Referências consultadas de taxa contratual base
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS tipo_base_referencia VARCHAR(50);
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS taxa_base_consultada NUMERIC(6, 4);
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS descricao_referencia VARCHAR(255);

-- 5. Artefatos explicativos (XAI e Python equivalente) e rastreabilidade da proposta
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS bloco_id VARCHAR(100);
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS trecho_origem TEXT;
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS explicacao TEXT;
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS python_equivalente TEXT;
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS pendencias TEXT;
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS completa BOOLEAN DEFAULT TRUE NOT NULL;

-- 6. Índices para otimização de consultas por campanha e bloco
CREATE INDEX IF NOT EXISTS idx_regra_campanha_id ON tb_regra (campanha_id) WHERE removido_em IS NULL;
CREATE INDEX IF NOT EXISTS idx_regra_bloco_id ON tb_regra (bloco_id) WHERE removido_em IS NULL;
