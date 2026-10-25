CREATE TABLE tb_execucao_calculo (
    id UUID PRIMARY KEY,
    chave_idempotencia VARCHAR(120) NOT NULL UNIQUE,
    competencia VARCHAR(7) NOT NULL,
    recalcular BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    total_itens INTEGER NOT NULL DEFAULT 0,
    itens_processados INTEGER NOT NULL DEFAULT 0,
    referencia_dados VARCHAR(64) NOT NULL,
    referencia_regras VARCHAR(64) NOT NULL,
    solicitado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    iniciado_em TIMESTAMP WITH TIME ZONE,
    concluido_em TIMESTAMP WITH TIME ZONE,
    falha TEXT,
    tentativa UUID NOT NULL,
    resultado TEXT
);
CREATE INDEX idx_execucao_calculo_competencia_status ON tb_execucao_calculo(competencia, status);

CREATE UNIQUE INDEX uk_execucao_calculo_ativa ON tb_execucao_calculo(competencia)
    WHERE status IN ('SOLICITADA', 'PROCESSANDO');
