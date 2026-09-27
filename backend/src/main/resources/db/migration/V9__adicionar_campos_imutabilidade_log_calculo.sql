-- =============================================================================
-- V9: Garantia de imutabilidade estrita e novos atributos de auditoria
-- BUG-23: Registro imutável de execução com snapshot de parâmetros, versão da regra,
-- identificação de lote de origem e distinção de venda informada vs importada.
-- =============================================================================

-- 1. Novos campos de auditoria e imutabilidade em tb_log_calculo_imutavel
ALTER TABLE tb_log_calculo_imutavel ADD COLUMN IF NOT EXISTS tipo_venda VARCHAR(50) NOT NULL DEFAULT 'INFORMADA';
ALTER TABLE tb_log_calculo_imutavel ADD COLUMN IF NOT EXISTS id_lote_origem VARCHAR(100);
ALTER TABLE tb_log_calculo_imutavel ADD COLUMN IF NOT EXISTS versao_regra VARCHAR(150);
ALTER TABLE tb_log_calculo_imutavel ADD COLUMN IF NOT EXISTS parametros_aplicados TEXT;

-- 2. Suporte à rastreabilidade de origem em tb_sales
ALTER TABLE tb_sales ADD COLUMN IF NOT EXISTS tipo_venda VARCHAR(50) DEFAULT 'INFORMADA';
ALTER TABLE tb_sales ADD COLUMN IF NOT EXISTS id_lote_origem VARCHAR(100);

-- 3. Índices adicionais para consulta e auditoria
CREATE INDEX IF NOT EXISTS idx_log_tipo_venda ON tb_log_calculo_imutavel (tipo_venda);
CREATE INDEX IF NOT EXISTS idx_log_id_lote ON tb_log_calculo_imutavel (id_lote_origem);

-- 4. Função e trigger para impedir rigorosamente UPDATE ou DELETE em tb_log_calculo_imutavel
CREATE OR REPLACE FUNCTION trg_impedir_alteracao_exclusao_log_calculo()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Operação não permitida: registros em tb_log_calculo_imutavel são estritamente imutáveis.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_log_calculo_imutavel_protect ON tb_log_calculo_imutavel;
CREATE TRIGGER trg_log_calculo_imutavel_protect
BEFORE UPDATE OR DELETE ON tb_log_calculo_imutavel
FOR EACH ROW
EXECUTE FUNCTION trg_impedir_alteracao_exclusao_log_calculo();
