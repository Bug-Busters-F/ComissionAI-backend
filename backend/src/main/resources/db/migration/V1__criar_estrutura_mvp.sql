-- =============================================================================
-- 1. CAMPANHAS E REGRAS DE NEGÓCIO
-- =============================================================================
CREATE TABLE tb_campanha (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    texto_original TEXT NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    removido_em TIMESTAMP WITH TIME ZONE,
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE
);

CREATE TABLE tb_regra (
    id BIGSERIAL PRIMARY KEY,
    campanha_id BIGINT REFERENCES tb_campanha(id),
    nome VARCHAR(255) NOT NULL,
    canal VARCHAR(100) NOT NULL,
    taxa NUMERIC(6, 4) NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ATIVA',
    removido_em TIMESTAMP WITH TIME ZONE,
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_regra_vigencia_canal ON tb_regra (canal, data_inicio, data_fim) 
    WHERE removido_em IS NULL;

-- =============================================================================
-- 2. ENVIOS, STATUS E RELATÓRIO DE CONSISTÊNCIA (UPLOAD)
-- =============================================================================
CREATE TABLE tb_envio_arquivo (
    id BIGSERIAL PRIMARY KEY,
    nome_arquivo VARCHAR(255) NOT NULL,
    tipo_base VARCHAR(50) NOT NULL, -- 'RH', 'VENDAS', 'TAXAS_BASE'
    hash_conteudo VARCHAR(64) NOT NULL,
    status VARCHAR(50) NOT NULL,    -- 'SUCESSO', 'REJEITADO', 'PROCESSADO_COM_AVISOS'
    total_linhas INT NOT NULL DEFAULT 0,
    linhas_validas INT NOT NULL DEFAULT 0,
    rejeicao_integral BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_envio_hash_tipo ON tb_envio_arquivo (hash_conteudo, tipo_base);

CREATE TABLE tb_inconsistencia_importacao (
    id BIGSERIAL PRIMARY KEY,
    envio_id BIGINT NOT NULL REFERENCES tb_envio_arquivo(id) ON DELETE CASCADE,
    linha INT NOT NULL,
    campo VARCHAR(100),
    motivo TEXT NOT NULL,
    severidade VARCHAR(20) NOT NULL -- 'IMPEDITIVO', 'AVISO'
);

CREATE INDEX idx_inconsistencia_envio ON tb_inconsistencia_importacao (envio_id);

-- =============================================================================
-- 3. BASES DE DADOS EFETIVADAS (COM ORIGEM PRESERVADA)
-- =============================================================================
CREATE TABLE tb_base_rh (
    id BIGSERIAL PRIMARY KEY,
    envio_id BIGINT REFERENCES tb_envio_arquivo(id),
    data_ref DATE NOT NULL,
    cod_marca INT NOT NULL,
    descr_marca VARCHAR(150) NOT NULL,
    cod_loja INT NOT NULL,
    descr_loja VARCHAR(150) NOT NULL,
    matricula VARCHAR(50) NOT NULL,
    data_admissao DATE NOT NULL,
    data_demissao DATE,
    cod_cargo INT NOT NULL,
    descri_cargo VARCHAR(150) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_rh_competencia_matricula ON tb_base_rh (data_ref, matricula);
CREATE INDEX idx_rh_matricula ON tb_base_rh (matricula);

CREATE TABLE tb_base_vendas (
    id BIGSERIAL PRIMARY KEY,
    envio_id BIGINT REFERENCES tb_envio_arquivo(id),
    data_venda DATE NOT NULL,
    cod_marca INT NOT NULL,
    descr_marca VARCHAR(150),
    cod_loja INT NOT NULL,
    descr_loja VARCHAR(150),
    matricula VARCHAR(50) NOT NULL,
    canal VARCHAR(100) DEFAULT 'LOJA_FISICA',
    valor_venda NUMERIC(15, 2) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_vendas_data_canal ON tb_base_vendas (data_venda, canal);
CREATE INDEX idx_vendas_matricula ON tb_base_vendas (matricula);
CREATE INDEX idx_vendas_chave_negocio ON tb_base_vendas (matricula, data_venda, cod_loja, cod_marca, valor_venda);

CREATE TABLE tb_taxa_marca_cargo (
    id BIGSERIAL PRIMARY KEY,
    envio_id BIGINT REFERENCES tb_envio_arquivo(id),
    cod_marca INT NOT NULL,
    descr_marca VARCHAR(150),
    cod_cargo INT NOT NULL,
    descri_cargo VARCHAR(150),
    percentual_comissao NUMERIC(6, 4) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_taxa_marca_cargo ON tb_taxa_marca_cargo (cod_marca, cod_cargo);

-- =============================================================================
-- 4. RESULTADOS DE CÁLCULO E LOGS IMUTÁVEIS
-- =============================================================================
CREATE TABLE tb_resultado_calculo (
    id BIGSERIAL PRIMARY KEY,
    protocolo_calculo UUID NOT NULL UNIQUE,
    matricula VARCHAR(50) NOT NULL,
    cod_marca INT,
    cod_loja INT,
    cod_cargo INT,
    regra_id BIGINT REFERENCES tb_regra(id),
    data_venda DATE NOT NULL,
    valor_venda NUMERIC(15, 2) NOT NULL,
    taxa_aplicada NUMERIC(6, 4) NOT NULL,
    valor_comissao NUMERIC(15, 2) NOT NULL,
    tipo_calculo VARCHAR(50) NOT NULL DEFAULT 'INDIVIDUAL',
    calculado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_resultado_calculo_venda_regra ON tb_resultado_calculo (matricula, data_venda, regra_id);

CREATE TABLE tb_log_calculo_imutavel (
    id UUID PRIMARY KEY,
    protocolo UUID NOT NULL,
    matricula VARCHAR(50) NOT NULL,
    cod_cargo INT,
    cod_loja INT,
    cod_marca INT,
    valor_venda NUMERIC(15, 2) NOT NULL,
    taxa_aplicada NUMERIC(6, 4) NOT NULL,
    valor_comissao NUMERIC(15, 2) NOT NULL,
    id_regra BIGINT NOT NULL,
    data_venda DATE NOT NULL,
    canal VARCHAR(100),
    origem_execucao VARCHAR(50) NOT NULL DEFAULT 'MOTOR_PRODUCAO',
    usuario_executor VARCHAR(100) DEFAULT 'SISTEMA',
    executado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_log_protocolo ON tb_log_calculo_imutavel (protocolo);
CREATE INDEX idx_log_matricula ON tb_log_calculo_imutavel (matricula);