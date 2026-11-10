-- =============================================================================
-- V30: Orquestração e Persistência de Simulações em Python (BUG-106 / BACK-11)
-- =============================================================================
-- Registra a rastreabilidade do programa Python executado na simulação de cenários,
-- vinculando o código-fonte homologado, versão do programa e hash SHA-256.
-- =============================================================================

ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS executado_via_python BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS codigo_python_executado TEXT;
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS checksum_python VARCHAR(64);
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS versao_programa_python INTEGER;
ALTER TABLE tb_simulacao ADD COLUMN IF NOT EXISTS saida_python_raw TEXT;

CREATE INDEX IF NOT EXISTS idx_simulacao_executado_python ON tb_simulacao(executado_via_python);
CREATE INDEX IF NOT EXISTS idx_simulacao_checksum_python ON tb_simulacao(checksum_python);

