-- =============================================================================
-- V28: Versionamento e Aprovação do Programa Python da Campanha (BUG-104 / BACK-9)
-- =============================================================================
-- Permite armazenar o código-fonte executável completo do programa Python da campanha,
-- mantendo histórico de versões de código, hash SHA-256 de integridade e auditoria
-- de aprovação/rejeição técnica formal antes da simulação e fechamento.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tb_campanha_programa_python (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campanha_versao_id BIGINT NOT NULL REFERENCES tb_campanha_versao(id) ON DELETE CASCADE,
    versao_programa INTEGER NOT NULL DEFAULT 1,
    codigo_fonte TEXT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    status_aprovacao VARCHAR(30) NOT NULL DEFAULT 'PENDENTE_REVISAO',
    aprovado_por VARCHAR(150),
    aprovado_em TIMESTAMP WITH TIME ZONE,
    rejeitado_por VARCHAR(150),
    rejeitado_em TIMESTAMP WITH TIME ZONE,
    justificativa TEXT,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_programa_python_versao UNIQUE (campanha_versao_id, versao_programa)
);

-- Vínculo na versão da campanha com o programa Python formalmente aprovado
ALTER TABLE tb_campanha_versao ADD COLUMN IF NOT EXISTS programa_python_aprovado_id UUID REFERENCES tb_campanha_programa_python(id) ON DELETE SET NULL;

-- Índices de consulta rápida
CREATE INDEX IF NOT EXISTS idx_programa_python_campanha_versao ON tb_campanha_programa_python(campanha_versao_id, versao_programa DESC);
CREATE INDEX IF NOT EXISTS idx_programa_python_status ON tb_campanha_programa_python(campanha_versao_id, status_aprovacao);
CREATE INDEX IF NOT EXISTS idx_campanha_versao_prog_aprovado ON tb_campanha_versao(programa_python_aprovado_id);

