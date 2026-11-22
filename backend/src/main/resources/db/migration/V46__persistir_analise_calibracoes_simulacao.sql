ALTER TABLE tb_simulacao ADD COLUMN analise_python_snapshot TEXT;
ALTER TABLE tb_simulacao ADD COLUMN solicitacao_python_id UUID;
ALTER TABLE tb_simulacao ADD COLUMN solicitacao_python_hash VARCHAR(64);
CREATE UNIQUE INDEX uk_simulacao_solicitacao_python ON tb_simulacao(solicitacao_python_id);
ALTER TABLE tb_campanha_versao ADD COLUMN revisao_execucao BIGINT NOT NULL DEFAULT 0;
