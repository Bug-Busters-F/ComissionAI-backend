-- =============================================================================
-- V12: Faixa de valor por venda e bônus (percentual e valor fixo) em tb_regra.
-- Resultado e log imutável registram as parcelas de bônus em campos próprios;
-- valor_comissao continua sendo o total (base + bônus).
-- =============================================================================

ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS valor_min NUMERIC(15, 2);
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS valor_max NUMERIC(15, 2);
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS min_inclusivo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS max_inclusivo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS bonus_percentual NUMERIC(6, 4);
ALTER TABLE tb_regra ADD COLUMN IF NOT EXISTS bonus_valor_fixo NUMERIC(15, 2);

ALTER TABLE tb_resultado_calculo ADD COLUMN IF NOT EXISTS valor_bonus_percentual NUMERIC(15, 2) NOT NULL DEFAULT 0;
ALTER TABLE tb_resultado_calculo ADD COLUMN IF NOT EXISTS valor_bonus_fixo NUMERIC(15, 2) NOT NULL DEFAULT 0;

ALTER TABLE tb_log_calculo_imutavel ADD COLUMN IF NOT EXISTS valor_bonus_percentual NUMERIC(15, 2) NOT NULL DEFAULT 0;
ALTER TABLE tb_log_calculo_imutavel ADD COLUMN IF NOT EXISTS valor_bonus_fixo NUMERIC(15, 2) NOT NULL DEFAULT 0;
