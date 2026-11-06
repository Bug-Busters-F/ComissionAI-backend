-- =============================================================================
-- V31: Persistir orçamento monetário e regras de enquadramento (BACK-8 / BUG-103)
-- =============================================================================

-- 1. Orçamento e encaminhamento na tb_campanha
ALTER TABLE tb_campanha ADD COLUMN orcamento NUMERIC(18, 2);
ALTER TABLE tb_campanha ADD COLUMN orcamento_extraido_ia NUMERIC(18, 2);
ALTER TABLE tb_campanha ADD COLUMN origem_orcamento VARCHAR(40);
ALTER TABLE tb_campanha ADD COLUMN orcamento_confirmado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tb_campanha ADD COLUMN orcamento_confirmado_por VARCHAR(120);
ALTER TABLE tb_campanha ADD COLUMN orcamento_confirmado_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE tb_campanha ADD COLUMN justificativa_orcamento TEXT;
ALTER TABLE tb_campanha ADD COLUMN encaminhada_gerente BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tb_campanha ADD COLUMN encaminhada_gerente_por VARCHAR(120);
ALTER TABLE tb_campanha ADD COLUMN encaminhada_gerente_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE tb_campanha ADD COLUMN observacao_encaminhamento TEXT;

-- 2. Orçamento e encaminhamento na tb_campanha_versao
ALTER TABLE tb_campanha_versao ADD COLUMN orcamento NUMERIC(18, 2);
ALTER TABLE tb_campanha_versao ADD COLUMN orcamento_extraido_ia NUMERIC(18, 2);
ALTER TABLE tb_campanha_versao ADD COLUMN origem_orcamento VARCHAR(40);
ALTER TABLE tb_campanha_versao ADD COLUMN orcamento_confirmado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tb_campanha_versao ADD COLUMN orcamento_confirmado_por VARCHAR(120);
ALTER TABLE tb_campanha_versao ADD COLUMN orcamento_confirmado_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE tb_campanha_versao ADD COLUMN justificativa_orcamento TEXT;
ALTER TABLE tb_campanha_versao ADD COLUMN encaminhada_gerente BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tb_campanha_versao ADD COLUMN encaminhada_gerente_por VARCHAR(120);
ALTER TABLE tb_campanha_versao ADD COLUMN encaminhada_gerente_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE tb_campanha_versao ADD COLUMN observacao_encaminhamento TEXT;

-- 3. Orçamento e enquadramento nos 4 cenários na tb_simulacao
ALTER TABLE tb_simulacao ADD COLUMN orcamento_campanha NUMERIC(18, 2);
ALTER TABLE tb_simulacao ADD COLUMN enquadrado_100 BOOLEAN;
ALTER TABLE tb_simulacao ADD COLUMN enquadrado_80 BOOLEAN;
ALTER TABLE tb_simulacao ADD COLUMN enquadrado_120 BOOLEAN;
ALTER TABLE tb_simulacao ADD COLUMN enquadrado_150 BOOLEAN;
ALTER TABLE tb_simulacao ADD COLUMN status_enquadramento_100 VARCHAR(30);
ALTER TABLE tb_simulacao ADD COLUMN saldo_orcamento_100 NUMERIC(18, 2);
ALTER TABLE tb_simulacao ADD COLUMN excedente_orcamento_100 NUMERIC(18, 2);
ALTER TABLE tb_simulacao ADD COLUMN percentual_uso_orcamento_100 NUMERIC(10, 2);
ALTER TABLE tb_simulacao ADD COLUMN escopo_total_vendas INTEGER;

-- 4. Orçamento consolidado e enquadramento na tb_execucao_calculo
ALTER TABLE tb_execucao_calculo ADD COLUMN orcamento_total_campanhas NUMERIC(18, 2);
ALTER TABLE tb_execucao_calculo ADD COLUMN comissao_total_apurada NUMERIC(18, 2);
ALTER TABLE tb_execucao_calculo ADD COLUMN status_enquadramento_execucao VARCHAR(30);
ALTER TABLE tb_execucao_calculo ADD COLUMN resumo_orcamento_json TEXT;
