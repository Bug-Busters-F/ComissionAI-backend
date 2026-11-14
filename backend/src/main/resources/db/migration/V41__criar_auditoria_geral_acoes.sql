-- =============================================================================
-- V33: Registro de Auditoria Geral das Ações Relevantes (BUG-111 / BACK-16)
-- =============================================================================
-- Cria a estrutura centralizada e imutável para registrar a trilha de auditoria
-- operacional e de compliance (quem aprovou código, alterou regras, executou
-- simulações, aceitou pendências, homologou versões e realizou fechamentos).
-- =============================================================================

CREATE TABLE IF NOT EXISTS tb_auditoria_geral (
    id UUID PRIMARY KEY,
    protocolo UUID NOT NULL UNIQUE,
    data_hora TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario VARCHAR(150) NOT NULL,
    perfil_usuario VARCHAR(50),
    tipo_acao VARCHAR(60) NOT NULL,
    tipo_entidade VARCHAR(60) NOT NULL,
    entidade_id VARCHAR(100),
    versao_entidade INTEGER,
    descricao TEXT NOT NULL,
    dados_anteriores TEXT,
    dados_novos TEXT,
    ip_origem VARCHAR(60),
    user_agent VARCHAR(255),
    sucesso BOOLEAN NOT NULL DEFAULT TRUE,
    detalhe_erro TEXT
);

-- Índices para buscas rápidas, filtragem de relatórios e trilha de compliance
CREATE INDEX IF NOT EXISTS idx_auditoria_protocolo ON tb_auditoria_geral(protocolo);
CREATE INDEX IF NOT EXISTS idx_auditoria_data_hora ON tb_auditoria_geral(data_hora);
CREATE INDEX IF NOT EXISTS idx_auditoria_usuario ON tb_auditoria_geral(usuario);
CREATE INDEX IF NOT EXISTS idx_auditoria_tipo_acao ON tb_auditoria_geral(tipo_acao);
CREATE INDEX IF NOT EXISTS idx_auditoria_entidade ON tb_auditoria_geral(tipo_entidade, entidade_id);

