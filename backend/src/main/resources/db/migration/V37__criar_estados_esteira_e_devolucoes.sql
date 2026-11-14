-- =============================================================================
-- V29: Estados da Esteira, Aprovações e Devoluções pelo Gerente (BUG-99 / BACK-4)
-- =============================================================================
-- Suporta o ciclo formal de governança com etapas 1 a 4, transições auditáveis,
-- devoluções com justificativa obrigatória e aprovação gerencial final.
-- =============================================================================

-- Campos de estado da esteira e devolução na versão da campanha
ALTER TABLE tb_campanha_versao ADD COLUMN IF NOT EXISTS estado_esteira VARCHAR(30) NOT NULL DEFAULT 'EM_ELABORACAO';
ALTER TABLE tb_campanha_versao ADD COLUMN IF NOT EXISTS devolvido_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE tb_campanha_versao ADD COLUMN IF NOT EXISTS devolvido_por VARCHAR(150);
ALTER TABLE tb_campanha_versao ADD COLUMN IF NOT EXISTS motivo_devolucao TEXT;
ALTER TABLE tb_campanha_versao ADD COLUMN IF NOT EXISTS etapa_devolucao INTEGER;
ALTER TABLE tb_campanha_versao ADD COLUMN IF NOT EXISTS gerente_aprovador VARCHAR(150);
ALTER TABLE tb_campanha_versao ADD COLUMN IF NOT EXISTS gerente_aprovado_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE tb_campanha_versao ADD COLUMN IF NOT EXISTS parecer_gerente TEXT;

-- Tabela de histórico de transições e movimentações na esteira (audit trail)
CREATE TABLE IF NOT EXISTS tb_campanha_esteira_transicao (
    id BIGSERIAL PRIMARY KEY,
    campanha_versao_id BIGINT NOT NULL REFERENCES tb_campanha_versao(id) ON DELETE CASCADE,
    etapa_origem INTEGER NOT NULL,
    etapa_destino INTEGER NOT NULL,
    estado_origem VARCHAR(30) NOT NULL,
    estado_destino VARCHAR(30) NOT NULL,
    tipo_acao VARCHAR(40) NOT NULL,
    responsavel VARCHAR(150) NOT NULL,
    motivo TEXT,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_esteira_transicao_versao ON tb_campanha_esteira_transicao(campanha_versao_id, criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_campanha_versao_estado_esteira ON tb_campanha_versao(estado_esteira);

