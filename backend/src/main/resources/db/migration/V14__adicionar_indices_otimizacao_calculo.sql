-- =============================================================================
-- V14: Índices de otimização de desempenho para apuração de competências (BACK-14)
-- =============================================================================

-- Índice para busca por intervalo de competência em tb_sales
CREATE INDEX IF NOT EXISTS idx_sales_sale_date ON tb_sales (sale_date);

-- Índice composto para carregamento e busca de taxas base vigentes
CREATE INDEX IF NOT EXISTS idx_basecomiss_lookup ON tb_basecomiss (brand_id, position_id, reference_month);

-- Índice para carregamento em lote de regras ativas vigentes da competência
CREATE INDEX IF NOT EXISTS idx_regra_vigencia_status ON tb_regra (status, data_inicio, data_fim) 
    WHERE removido_em IS NULL;

-- Índice para busca de resultados já calculados por data de venda
CREATE INDEX IF NOT EXISTS idx_resultado_calculo_data_venda ON tb_resultado_calculo (data_venda);
