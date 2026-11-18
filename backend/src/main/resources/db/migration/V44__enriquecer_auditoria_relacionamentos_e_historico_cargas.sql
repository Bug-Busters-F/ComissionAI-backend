-- =============================================================================
-- V44: Enriquecimento de relacionamentos em auditoria geral, autoria de cargas
--      e histórico permanente de validações/exclusões (BACK-17 / FRONT-12)
-- =============================================================================

-- 1. Relacionamentos estruturados e indexados na trilha unificada de auditoria
ALTER TABLE tb_auditoria_geral
    ADD COLUMN IF NOT EXISTS campanha_id BIGINT,
    ADD COLUMN IF NOT EXISTS campanha_versao_id BIGINT,
    ADD COLUMN IF NOT EXISTS campanha_titulo VARCHAR(255),
    ADD COLUMN IF NOT EXISTS envio_arquivo_id BIGINT,
    ADD COLUMN IF NOT EXISTS arquivo_importacao_id UUID,
    ADD COLUMN IF NOT EXISTS lote_preparacao_id UUID,
    ADD COLUMN IF NOT EXISTS id_lote VARCHAR(100),
    ADD COLUMN IF NOT EXISTS report_id BIGINT,
    ADD COLUMN IF NOT EXISTS nome_arquivo VARCHAR(255),
    ADD COLUMN IF NOT EXISTS simulacao_id UUID,
    ADD COLUMN IF NOT EXISTS apuracao_campanha_id UUID,
    ADD COLUMN IF NOT EXISTS execucao_calculo_id UUID,
    ADD COLUMN IF NOT EXISTS fechamento_id UUID,
    ADD COLUMN IF NOT EXISTS protocolo_calculo UUID,
    ADD COLUMN IF NOT EXISTS log_calculo_id UUID,
    ADD COLUMN IF NOT EXISTS competencia VARCHAR(7);

CREATE INDEX IF NOT EXISTS idx_auditoria_perfil_data ON tb_auditoria_geral (perfil_usuario, data_hora DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_campanha ON tb_auditoria_geral (campanha_id, data_hora DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_envio_arquivo ON tb_auditoria_geral (envio_arquivo_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_report_id ON tb_auditoria_geral (report_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_id_lote ON tb_auditoria_geral (id_lote);
CREATE INDEX IF NOT EXISTS idx_auditoria_calculo ON tb_auditoria_geral (protocolo_calculo);
CREATE INDEX IF NOT EXISTS idx_auditoria_execucao ON tb_auditoria_geral (execucao_calculo_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_fechamento ON tb_auditoria_geral (fechamento_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_competencia ON tb_auditoria_geral (competencia);

-- 2. Autoria permanente de cargas e histórico preservado de exclusões
ALTER TABLE tb_envio_arquivo
    ADD COLUMN IF NOT EXISTS criado_por VARCHAR(150) NOT NULL DEFAULT 'SISTEMA',
    ADD COLUMN IF NOT EXISTS perfil_criador VARCHAR(50),
    ADD COLUMN IF NOT EXISTS motivo_exclusao TEXT,
    ADD COLUMN IF NOT EXISTS perfil_exclusao VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_envio_arquivo_criado_por ON tb_envio_arquivo (criado_por);
CREATE INDEX IF NOT EXISTS idx_envio_arquivo_criado_em ON tb_envio_arquivo (criado_em DESC);

-- 3. Rastreabilidade de solicitante nas execuções assíncronas de cálculo
ALTER TABLE tb_execucao_calculo
    ADD COLUMN IF NOT EXISTS solicitado_por VARCHAR(150) NOT NULL DEFAULT 'SISTEMA',
    ADD COLUMN IF NOT EXISTS perfil_solicitante VARCHAR(50);

-- 4. Carimbo de criação e índice composto para consultas paginadas de validações
ALTER TABLE tb_inconsistencia_importacao
    ADD COLUMN IF NOT EXISTS criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_inconsistencia_envio_sev_linha
    ON tb_inconsistencia_importacao (envio_id, severidade, linha);
