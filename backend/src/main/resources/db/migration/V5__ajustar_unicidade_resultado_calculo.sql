-- =============================================================================
-- V5: Ajuste de unicidade em tb_resultado_calculo
-- =============================================================================
-- Contexto: A restrição original uk_resultado_calculo_venda_regra impedia que um
-- mesmo colaborador tivesse mais de uma venda na mesma data calculada.
-- Com a introdução do id_venda (UUID da Sale em S1-B07), a unicidade para vendas
-- identificadas passa a ser por id_venda, mantendo a regra por (matricula, data, regra)
-- apenas para requisições legadas onde id_venda é NULL.
-- =============================================================================

DROP INDEX IF EXISTS uk_resultado_calculo_venda_regra;

CREATE UNIQUE INDEX IF NOT EXISTS uk_resultado_calculo_venda_regra_sem_id 
    ON tb_resultado_calculo (matricula, data_venda, regra_id) 
    WHERE id_venda IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_resultado_calculo_id_venda 
    ON tb_resultado_calculo (id_venda) 
    WHERE id_venda IS NOT NULL;
