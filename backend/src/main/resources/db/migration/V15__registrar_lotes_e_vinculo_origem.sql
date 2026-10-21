-- =============================================================================
-- V15: Registro de lotes de importação, vínculo de origem e trava de competência
-- =============================================================================

-- 1. Novos status em tb_status_envio
INSERT INTO tb_status_envio (codigo, descricao) VALUES
    ('PENDENTE', 'Lote aguardando início do processamento'),
    ('PROCESSANDO', 'Lote em processamento'),
    ('CONCLUIDO', 'Lote processado com sucesso'),
    ('FALHOU', 'Processamento do lote falhou'),
    ('EXCLUIDO', 'Lote excluído e dados removidos')
ON CONFLICT (codigo) DO NOTHING;

-- 2. Compatibilização de tipos de base em tb_tipo_base
INSERT INTO tb_tipo_base (codigo, descricao) VALUES
    ('HR', 'Base de Recursos Humanos'),
    ('SALES', 'Base de Transações de Vendas'),
    ('COMISSIONS', 'Tabela de Taxas Base de Comissões')
ON CONFLICT (codigo) DO NOTHING;

-- 3. Evolução de tb_envio_arquivo
ALTER TABLE tb_envio_arquivo
    ALTER COLUMN hash_conteudo DROP NOT NULL;

DROP INDEX IF EXISTS uk_envio_hash_tipo;

ALTER TABLE tb_envio_arquivo
    ADD COLUMN IF NOT EXISTS id_lote VARCHAR(100),
    ADD COLUMN IF NOT EXISTS report_id BIGINT,
    ADD COLUMN IF NOT EXISTS competencia VARCHAR(7),
    ADD COLUMN IF NOT EXISTS mensagem TEXT,
    ADD COLUMN IF NOT EXISTS iniciado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS finalizado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS excluido_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS excluido_por VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_envio_arquivo_id_lote ON tb_envio_arquivo (id_lote) WHERE id_lote IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_envio_arquivo_report_id ON tb_envio_arquivo (report_id) WHERE report_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_envio_arquivo_competencia ON tb_envio_arquivo (competencia);
CREATE INDEX IF NOT EXISTS idx_envio_arquivo_status ON tb_envio_arquivo (status);

-- 4. Vínculo de origem nas entidades importadas
ALTER TABLE tb_registration ADD COLUMN IF NOT EXISTS id_lote_origem VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_registration_lote_origem ON tb_registration (id_lote_origem);

ALTER TABLE tb_basecomiss ADD COLUMN IF NOT EXISTS id_lote_origem VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_basecomiss_lote_origem ON tb_basecomiss (id_lote_origem);

CREATE INDEX IF NOT EXISTS idx_sales_lote_origem ON tb_sales (id_lote_origem);

-- 5. Índices para otimização de verificação de dependências
CREATE INDEX IF NOT EXISTS idx_log_id_lote_origem ON tb_log_calculo_imutavel (id_lote_origem);
CREATE INDEX IF NOT EXISTS idx_resultado_marca_cargo_data ON tb_resultado_calculo (cod_marca, cod_cargo, data_venda);

-- 6. Tabela de trava de concorrência por competência
CREATE TABLE IF NOT EXISTS tb_trava_competencia (
    competencia VARCHAR(7) PRIMARY KEY,
    bloqueado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. Carga retroativa para dados legados a partir do Spring Batch
INSERT INTO tb_envio_arquivo (
    id_lote, report_id, nome_arquivo, tipo_base, hash_conteudo, status,
    total_linhas, linhas_validas, rejeicao_integral, competencia,
    criado_em, iniciado_em, finalizado_em
)
SELECT
    COALESCE(p_req.parameter_value, gen_random_uuid()::text) AS id_lote,
    e.job_execution_id AS report_id,
    COALESCE(p_file.parameter_value, 'arquivo_legado') AS nome_arquivo,
    COALESCE(p_type.parameter_value, 'SALES') AS tipo_base,
    'LEGACY' AS hash_conteudo,
    CASE
        WHEN e.status = 'COMPLETED' THEN 'CONCLUIDO'
        WHEN e.status = 'FAILED' THEN 'FALHOU'
        ELSE 'CONCLUIDO'
    END AS status,
    COALESCE((SELECT SUM(s.write_count) FROM batch_step_execution s WHERE s.job_execution_id = e.job_execution_id), 0) AS total_linhas,
    COALESCE((SELECT SUM(s.write_count) FROM batch_step_execution s WHERE s.job_execution_id = e.job_execution_id), 0) AS linhas_validas,
    CASE WHEN e.status = 'FAILED' THEN TRUE ELSE FALSE END AS rejeicao_integral,
    p_comp.parameter_value AS competencia,
    e.create_time AS criado_em,
    e.start_time AS iniciado_em,
    e.end_time AS finalizado_em
FROM batch_job_execution e
LEFT JOIN batch_job_execution_params p_req ON p_req.job_execution_id = e.job_execution_id AND p_req.parameter_name = 'requestId'
LEFT JOIN batch_job_execution_params p_file ON p_file.job_execution_id = e.job_execution_id AND p_file.parameter_name = 'filename'
LEFT JOIN batch_job_execution_params p_type ON p_type.job_execution_id = e.job_execution_id AND p_type.parameter_name = 'importType'
LEFT JOIN batch_job_execution_params p_comp ON p_comp.job_execution_id = e.job_execution_id AND p_comp.parameter_name = 'competencia'
WHERE NOT EXISTS (SELECT 1 FROM tb_envio_arquivo a WHERE a.report_id = e.job_execution_id);
