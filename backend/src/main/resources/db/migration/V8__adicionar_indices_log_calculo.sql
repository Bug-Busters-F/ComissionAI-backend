-- =============================================================================
-- V8: Índices de suporte e integridade para consulta e auditoria de logs de cálculo
-- =============================================================================

ALTER TABLE tb_log_calculo_imutavel
    ADD COLUMN IF NOT EXISTS id_venda UUID;

ALTER TABLE tb_resultado_calculo
    ADD COLUMN IF NOT EXISTS id_venda UUID;

CREATE INDEX IF NOT EXISTS idx_log_id_venda ON tb_log_calculo_imutavel (id_venda);
CREATE INDEX IF NOT EXISTS idx_log_id_regra ON tb_log_calculo_imutavel (id_regra);
CREATE INDEX IF NOT EXISTS idx_log_data_venda ON tb_log_calculo_imutavel (data_venda);
CREATE INDEX IF NOT EXISTS idx_log_executado_em ON tb_log_calculo_imutavel (executado_em DESC);