-- =============================================================================
-- V2: Tabela de vendas individuais (entrada via API, sem vínculo a envio de arquivo)
-- =============================================================================
-- Contexto: As vendas importadas em lote residem em tb_base_vendas, que exige
-- vínculo obrigatório (FK NOT NULL) com tb_envio_arquivo. Para suportar a entrada
-- individual de vendas via API REST sem depender de um processo de upload, criamos
-- esta tabela dedicada, com unicidade em id_venda_externo para garantir idempotência
-- na etapa de cálculo de comissionamento.
-- =============================================================================

CREATE TABLE tb_venda (
    id              BIGSERIAL PRIMARY KEY,
    id_venda_externo VARCHAR(100) NOT NULL,
    matricula        VARCHAR(50)  NOT NULL,
    canal            VARCHAR(100) NOT NULL,
    marca            VARCHAR(150) NOT NULL,
    loja             VARCHAR(150) NOT NULL,
    data_venda       DATE         NOT NULL,
    valor_venda      NUMERIC(15, 2) NOT NULL,
    criado_em        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Restrição de unicidade no identificador externo: impede registros duplicados
-- e serve de base para a lógica de idempotência no cálculo de comissão.
ALTER TABLE tb_venda
    ADD CONSTRAINT uk_venda_id_externo UNIQUE (id_venda_externo);

-- Índices de suporte para cruzamento com regras de comissionamento (canal + data)
-- e para rastreabilidade por funcionário.
CREATE INDEX idx_venda_data_canal ON tb_venda (data_venda, canal);
CREATE INDEX idx_venda_matricula  ON tb_venda (matricula);
