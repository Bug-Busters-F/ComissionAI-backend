-- =============================================================================
-- V33: Área de Preparação, Edição Validada, Preservação de Arquivos e
--      Versionamento de Registros das Bases Globais (BACK-5)
-- =============================================================================

-- 1. Armazenamento permanente de arquivos originais (não se perde ao apagar temp file do Batch)
CREATE TABLE IF NOT EXISTS tb_arquivo_importacao (
    id UUID PRIMARY KEY,
    hash_conteudo VARCHAR(64) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(120),
    tamanho_bytes BIGINT NOT NULL,
    conteudo BYTEA NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_por VARCHAR(150) NOT NULL DEFAULT 'SISTEMA'
);

CREATE INDEX IF NOT EXISTS idx_arquivo_importacao_hash ON tb_arquivo_importacao (hash_conteudo);

-- 2. Evolução de tb_envio_arquivo para vínculo com arquivo preservado e cadeia de substituição
ALTER TABLE tb_envio_arquivo
    ADD COLUMN IF NOT EXISTS arquivo_importacao_id UUID REFERENCES tb_arquivo_importacao(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS envio_anterior_id BIGINT REFERENCES tb_envio_arquivo(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS substituido_por_id BIGINT REFERENCES tb_envio_arquivo(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS origem_lote VARCHAR(30) NOT NULL DEFAULT 'IMPORTACAO_DIRETA';

-- Sequência para gerar report_id único quando um lote é efetivado via preparação (sem job do Spring Batch)
CREATE SEQUENCE IF NOT EXISTS seq_envio_arquivo_report_id START WITH 1000000 INCREMENT BY 1;

-- 3. Evolução de tb_registration e tb_sales para controle de versão e origem
ALTER TABLE tb_registration
    ADD COLUMN IF NOT EXISTS versao INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS origem_ultima_alteracao VARCHAR(30) NOT NULL DEFAULT 'IMPORTACAO',
    ADD COLUMN IF NOT EXISTS id_lote_ultima_alteracao VARCHAR(100),
    ADD COLUMN IF NOT EXISTS atualizado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS atualizado_por VARCHAR(150);

ALTER TABLE tb_sales
    ADD COLUMN IF NOT EXISTS versao INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS origem_ultima_alteracao VARCHAR(30) NOT NULL DEFAULT 'IMPORTACAO',
    ADD COLUMN IF NOT EXISTS id_lote_ultima_alteracao VARCHAR(100),
    ADD COLUMN IF NOT EXISTS atualizado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS atualizado_por VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_sales_dedup_chave
    ON tb_sales (registration_id, sale_date, store_id, brand_id, valor_venda);

-- 4. Tabela de Lotes em Preparação vinculados à Campanha
CREATE TABLE IF NOT EXISTS tb_lote_preparacao (
    id UUID PRIMARY KEY,
    campanha_id BIGINT NOT NULL REFERENCES tb_campanha(id) ON DELETE RESTRICT,
    tipo_base VARCHAR(20) NOT NULL,
    competencia VARCHAR(7),
    nome_arquivo VARCHAR(255) NOT NULL,
    hash_conteudo VARCHAR(64) NOT NULL,
    arquivo_importacao_id UUID NOT NULL REFERENCES tb_arquivo_importacao(id) ON DELETE RESTRICT,
    envio_arquivo_origem_id BIGINT REFERENCES tb_envio_arquivo(id) ON DELETE SET NULL,
    lote_preparacao_anterior_id UUID REFERENCES tb_lote_preparacao(id) ON DELETE SET NULL,
    envio_arquivo_efetivado_id BIGINT REFERENCES tb_envio_arquivo(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'EM_PREPARACAO',
    origem_criacao VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    versao_preparo INT NOT NULL DEFAULT 1,
    total_linhas INT NOT NULL DEFAULT 0,
    linhas_validas INT NOT NULL DEFAULT 0,
    linhas_invalidas INT NOT NULL DEFAULT 0,
    linhas_impeditivas INT NOT NULL DEFAULT 0,
    linhas_advertencia INT NOT NULL DEFAULT 0,
    linhas_aviso INT NOT NULL DEFAULT 0,
    linhas_editadas INT NOT NULL DEFAULT 0,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_por VARCHAR(150) NOT NULL DEFAULT 'SISTEMA',
    revalidado_em TIMESTAMP WITH TIME ZONE,
    efetivado_em TIMESTAMP WITH TIME ZONE,
    efetivado_por VARCHAR(150),
    descartado_em TIMESTAMP WITH TIME ZONE,
    descartado_por VARCHAR(150),
    motivo_descarte TEXT,
    CONSTRAINT chk_lote_prep_tipo_base CHECK (tipo_base IN ('HR', 'SALES')),
    CONSTRAINT chk_lote_prep_status CHECK (status IN ('EM_PREPARACAO', 'EFETIVADO', 'DESCARTADO')),
    CONSTRAINT chk_lote_prep_competencia CHECK (
        (tipo_base = 'HR' AND competencia IS NULL)
        OR (tipo_base = 'SALES' AND competencia IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_lote_prep_campanha_status
    ON tb_lote_preparacao (campanha_id, status, criado_em DESC);

-- 5. Tabela de Linhas em Preparação (todas as linhas: válidas e inválidas)
CREATE TABLE IF NOT EXISTS tb_linha_preparacao (
    id UUID PRIMARY KEY,
    lote_preparacao_id UUID NOT NULL REFERENCES tb_lote_preparacao(id) ON DELETE CASCADE,
    numero_linha INT NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    status_linha VARCHAR(30) NOT NULL DEFAULT 'VALIDA',
    severidade_maxima VARCHAR(20),
    origem_ultima_edicao VARCHAR(30) NOT NULL DEFAULT 'ORIGINAL',
    editada BOOLEAN NOT NULL DEFAULT FALSE,
    ignorar_na_efetivacao BOOLEAN NOT NULL DEFAULT FALSE,
    registro_alvo_id UUID,

    -- Conteúdo bruto original da planilha (JSON)
    conteudo_original_json TEXT NOT NULL,

    -- Campos interpretados / editáveis (unificados para HR e SALES para filtros indexados)
    data_referencia DATE,
    cod_marca INT,
    descr_marca VARCHAR(150),
    cod_loja INT,
    descr_loja VARCHAR(150),
    matricula VARCHAR(100),
    data_admissao DATE,
    data_demissao DATE,
    cod_cargo INT,
    descr_cargo VARCHAR(150),
    valor_venda NUMERIC(15, 2),

    -- Lista de erros de validação da linha (JSON: [{campo, motivo, severidade}])
    erros_validacao_json TEXT NOT NULL DEFAULT '[]',
    campos_com_erro VARCHAR(255),

    atualizado_em TIMESTAMP WITH TIME ZONE,
    atualizado_por VARCHAR(150),

    CONSTRAINT uk_linha_prep_lote_numero UNIQUE (lote_preparacao_id, numero_linha),
    CONSTRAINT chk_linha_prep_status CHECK (status_linha IN ('VALIDA', 'INVALIDA', 'IGNORADA'))
);

CREATE INDEX IF NOT EXISTS idx_linha_prep_lote_valida
    ON tb_linha_preparacao (lote_preparacao_id, status_linha, severidade_maxima);
CREATE INDEX IF NOT EXISTS idx_linha_prep_lote_matricula
    ON tb_linha_preparacao (lote_preparacao_id, matricula);
CREATE INDEX IF NOT EXISTS idx_linha_prep_lote_data
    ON tb_linha_preparacao (lote_preparacao_id, data_referencia);

-- 6. Histórico de Edições das Linhas na Preparação
CREATE TABLE IF NOT EXISTS tb_historico_edicao_preparacao (
    id UUID PRIMARY KEY,
    linha_preparacao_id UUID NOT NULL REFERENCES tb_linha_preparacao(id) ON DELETE CASCADE,
    lote_preparacao_id UUID NOT NULL REFERENCES tb_lote_preparacao(id) ON DELETE CASCADE,
    numero_linha INT NOT NULL,
    versao_anterior BIGINT NOT NULL,
    versao_nova BIGINT NOT NULL,
    origem VARCHAR(30) NOT NULL,
    valores_anteriores_json TEXT NOT NULL,
    valores_novos_json TEXT NOT NULL,
    campos_alterados VARCHAR(255) NOT NULL,
    erros_antes_json TEXT,
    erros_depois_json TEXT,
    motivo TEXT,
    editado_por VARCHAR(150) NOT NULL,
    editado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_hist_edicao_origem CHECK (origem IN ('MANUAL', 'IA'))
);

CREATE INDEX IF NOT EXISTS idx_hist_edicao_linha
    ON tb_historico_edicao_preparacao (linha_preparacao_id, editado_em DESC);

-- 7. Histórico Imutável de Versões dos Registros na Base Geral (tb_registration e tb_sales)
CREATE TABLE IF NOT EXISTS tb_historico_versao_registro (
    id UUID PRIMARY KEY,
    tipo_base VARCHAR(20) NOT NULL,
    registro_id UUID NOT NULL,
    chave_negocio VARCHAR(255) NOT NULL,
    versao INT NOT NULL,
    tipo_operacao VARCHAR(30) NOT NULL,
    origem VARCHAR(30) NOT NULL,
    campanha_id BIGINT REFERENCES tb_campanha(id) ON DELETE SET NULL,
    lote_preparacao_id UUID REFERENCES tb_lote_preparacao(id) ON DELETE SET NULL,
    id_lote VARCHAR(100),
    valores_anteriores_json TEXT,
    valores_novos_json TEXT NOT NULL,
    registrado_por VARCHAR(150) NOT NULL,
    registrado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_hist_versao_registro UNIQUE (tipo_base, registro_id, versao)
);

CREATE INDEX IF NOT EXISTS idx_hist_versao_lookup
    ON tb_historico_versao_registro (tipo_base, registro_id, versao DESC);
CREATE INDEX IF NOT EXISTS idx_hist_versao_chave
    ON tb_historico_versao_registro (tipo_base, chave_negocio, versao DESC);
