-- =============================================================================
-- V31: Aceite Rastreável de Pendências e Riscos (BUG-102 / BACK-7)
-- =============================================================================
-- Permite registrar formalmente o aceite fundamentado de inconsistências,
-- divergências ou pendências cadastrais/regras antes de avançar a simulação
-- ou homologar a esteira, com trilha de auditoria completa e revogabilidade.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tb_campanha_pendencia_aceite (
    id BIGSERIAL PRIMARY KEY,
    campanha_id BIGINT NOT NULL REFERENCES tb_campanha(id) ON DELETE CASCADE,
    campanha_versao_id BIGINT REFERENCES tb_campanha_versao(id) ON DELETE CASCADE,
    regra_id BIGINT REFERENCES tb_regra(id) ON DELETE CASCADE,
    pendencia_identificador VARCHAR(150) NOT NULL,
    codigo VARCHAR(100) NOT NULL,
    campo VARCHAR(100),
    mensagem TEXT,
    severidade VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACEITA',
    aceito_por VARCHAR(150) NOT NULL,
    aceito_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo_aceite TEXT NOT NULL,
    revogado_por VARCHAR(150),
    revogado_em TIMESTAMP WITH TIME ZONE,
    motivo_revogacao TEXT
);

CREATE INDEX IF NOT EXISTS idx_pendencia_aceite_campanha ON tb_campanha_pendencia_aceite(campanha_id, status);
CREATE INDEX IF NOT EXISTS idx_pendencia_aceite_versao ON tb_campanha_pendencia_aceite(campanha_versao_id);
CREATE INDEX IF NOT EXISTS idx_pendencia_aceite_regra ON tb_campanha_pendencia_aceite(regra_id);
CREATE INDEX IF NOT EXISTS idx_pendencia_aceite_ident ON tb_campanha_pendencia_aceite(pendencia_identificador);

