-- =============================================================================
-- V15: Estruturas dedicadas para persistência de simulações em sandbox (BACK-7)
-- =============================================================================
-- Registra a execução de simulações, premissas, snapshot de regras, memória de
-- cálculo e resultados consolidados, mantendo isolamento absoluto das tabelas
-- de comissões e logs de produção (tb_resultado_calculo e tb_log_calculo_imutavel).
-- =============================================================================

CREATE TABLE tb_simulacao (
    id UUID PRIMARY KEY,
    protocolo UUID NOT NULL UNIQUE,
    executado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    competencia VARCHAR(10) NOT NULL,
    campanha_id BIGINT REFERENCES tb_campanha(id) ON DELETE SET NULL,
    titulo_campanha VARCHAR(255),
    data_inicio_campanha DATE NOT NULL,
    data_fim_campanha DATE NOT NULL,
    dias_inclusivos BIGINT NOT NULL,
    fator_projecao NUMERIC(10, 6) NOT NULL,
    origem_dados VARCHAR(100) NOT NULL DEFAULT 'HISTORICO_VENDAS_COMPETENCIA',
    premissas_snapshot TEXT NOT NULL,
    memoria_calculo_consolidada TEXT,
    total_vendas_recortadas INT NOT NULL,
    valor_total_recortado NUMERIC(15, 2) NOT NULL,

    -- Cenário 100% (Proposta Revisada vs Base)
    total_comissao_base NUMERIC(15, 2) NOT NULL,
    total_comissao_proposta NUMERIC(15, 2) NOT NULL,
    delta_comissao NUMERIC(15, 2) NOT NULL,
    delta_percentual NUMERIC(10, 2) NOT NULL,
    taxa_media_efetiva_base NUMERIC(6, 4) NOT NULL,
    taxa_media_efetiva_proposta NUMERIC(6, 4) NOT NULL,
    comissao_projetada NUMERIC(15, 2) NOT NULL,
    delta_projetado NUMERIC(15, 2) NOT NULL,
    qtd_vendas_com_mudanca_faixa INT NOT NULL DEFAULT 0,
    qtd_vendas_com_regra_aplicada INT NOT NULL DEFAULT 0,
    qtd_vendas_com_fallback_base INT NOT NULL DEFAULT 0,
    qtd_vendas_com_impedimento INT NOT NULL DEFAULT 0,

    -- Cenário 80% (Volume Reduzido)
    vendas_80 NUMERIC(15, 2) NOT NULL,
    comissao_base_80 NUMERIC(15, 2) NOT NULL,
    comissao_proposta_80 NUMERIC(15, 2) NOT NULL,
    delta_80 NUMERIC(15, 2) NOT NULL,
    delta_perc_80 NUMERIC(10, 2) NOT NULL,
    taxa_media_80 NUMERIC(6, 4) NOT NULL,
    projetada_80 NUMERIC(15, 2) NOT NULL,
    delta_proj_80 NUMERIC(15, 2) NOT NULL,
    mudanca_faixa_80 INT NOT NULL DEFAULT 0,

    -- Cenário 120% (Crescimento Otimista)
    vendas_120 NUMERIC(15, 2) NOT NULL,
    comissao_base_120 NUMERIC(15, 2) NOT NULL,
    comissao_proposta_120 NUMERIC(15, 2) NOT NULL,
    delta_120 NUMERIC(15, 2) NOT NULL,
    delta_perc_120 NUMERIC(10, 2) NOT NULL,
    taxa_media_120 NUMERIC(6, 4) NOT NULL,
    projetada_120 NUMERIC(15, 2) NOT NULL,
    delta_proj_120 NUMERIC(15, 2) NOT NULL,
    mudanca_faixa_120 INT NOT NULL DEFAULT 0,

    -- Cenário 150% (Alta Performance)
    vendas_150 NUMERIC(15, 2) NOT NULL,
    comissao_base_150 NUMERIC(15, 2) NOT NULL,
    comissao_proposta_150 NUMERIC(15, 2) NOT NULL,
    delta_150 NUMERIC(15, 2) NOT NULL,
    delta_perc_150 NUMERIC(10, 2) NOT NULL,
    taxa_media_150 NUMERIC(6, 4) NOT NULL,
    projetada_150 NUMERIC(15, 2) NOT NULL,
    delta_proj_150 NUMERIC(15, 2) NOT NULL,
    mudanca_faixa_150 INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_simulacao_competencia ON tb_simulacao (competencia);
CREATE INDEX idx_simulacao_campanha_id ON tb_simulacao (campanha_id);
CREATE INDEX idx_simulacao_executado_em ON tb_simulacao (executado_em DESC);

CREATE TABLE tb_simulacao_venda (
    id UUID PRIMARY KEY,
    simulacao_id UUID NOT NULL REFERENCES tb_simulacao(id) ON DELETE CASCADE,
    id_venda UUID NOT NULL,
    matricula VARCHAR(50),
    cod_cargo INT,
    descri_cargo VARCHAR(255),
    cod_loja INT,
    cod_marca INT,
    descr_marca VARCHAR(255),
    canal VARCHAR(50),
    data_venda DATE NOT NULL,
    valor_venda_original NUMERIC(15, 2) NOT NULL,
    taxa_base_contratual NUMERIC(6, 4) NOT NULL,
    valor_comissao_base NUMERIC(15, 2) NOT NULL,

    -- Cenário 100%
    regra_id_100 BIGINT,
    regra_nome_100 VARCHAR(255),
    origem_taxa_100 VARCHAR(50) NOT NULL,
    atende_faixa_100 BOOLEAN NOT NULL DEFAULT TRUE,
    taxa_aplicada_100 NUMERIC(6, 4) NOT NULL,
    valor_bonus_perc_100 NUMERIC(15, 2) NOT NULL DEFAULT 0,
    valor_bonus_fixo_100 NUMERIC(15, 2) NOT NULL DEFAULT 0,
    valor_comissao_100 NUMERIC(15, 2) NOT NULL,
    delta_comissao_100 NUMERIC(15, 2) NOT NULL,

    -- Cenário 80%
    valor_80 NUMERIC(15, 2) NOT NULL,
    regra_id_80 BIGINT,
    regra_nome_80 VARCHAR(255),
    origem_80 VARCHAR(50) NOT NULL,
    taxa_80 NUMERIC(6, 4) NOT NULL,
    bonus_perc_80 NUMERIC(15, 2) NOT NULL DEFAULT 0,
    bonus_fixo_80 NUMERIC(15, 2) NOT NULL DEFAULT 0,
    comissao_80 NUMERIC(15, 2) NOT NULL,
    mudou_faixa_80 BOOLEAN NOT NULL DEFAULT FALSE,

    -- Cenário 120%
    valor_120 NUMERIC(15, 2) NOT NULL,
    regra_id_120 BIGINT,
    regra_nome_120 VARCHAR(255),
    origem_120 VARCHAR(50) NOT NULL,
    taxa_120 NUMERIC(6, 4) NOT NULL,
    bonus_perc_120 NUMERIC(15, 2) NOT NULL DEFAULT 0,
    bonus_fixo_120 NUMERIC(15, 2) NOT NULL DEFAULT 0,
    comissao_120 NUMERIC(15, 2) NOT NULL,
    mudou_faixa_120 BOOLEAN NOT NULL DEFAULT FALSE,

    -- Cenário 150%
    valor_150 NUMERIC(15, 2) NOT NULL,
    regra_id_150 BIGINT,
    regra_nome_150 VARCHAR(255),
    origem_150 VARCHAR(50) NOT NULL,
    taxa_150 NUMERIC(6, 4) NOT NULL,
    bonus_perc_150 NUMERIC(15, 2) NOT NULL DEFAULT 0,
    bonus_fixo_150 NUMERIC(15, 2) NOT NULL DEFAULT 0,
    comissao_150 NUMERIC(15, 2) NOT NULL,
    mudou_faixa_150 BOOLEAN NOT NULL DEFAULT FALSE,

    valida BOOLEAN NOT NULL DEFAULT TRUE,
    impedimentos TEXT,
    memoria_calculo_item TEXT NOT NULL
);

CREATE INDEX idx_simulacao_venda_simulacao ON tb_simulacao_venda (simulacao_id);
CREATE INDEX idx_simulacao_venda_id_venda ON tb_simulacao_venda (id_venda);
CREATE INDEX idx_simulacao_venda_matricula ON tb_simulacao_venda (matricula);
