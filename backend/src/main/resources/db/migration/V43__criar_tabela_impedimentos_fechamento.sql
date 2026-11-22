CREATE TABLE IF NOT EXISTS tb_impedimento_fechamento (
    id UUID PRIMARY KEY,
    fechamento_id UUID NOT NULL REFERENCES tb_fechamento_competencia(id) ON DELETE CASCADE,
    competencia VARCHAR(7) NOT NULL,
    id_venda UUID NOT NULL,
    matricula VARCHAR(50),
    cod_marca INTEGER,
    data_venda DATE,
    valor_venda NUMERIC(15, 2),
    motivo TEXT NOT NULL,
    tipo_impedimento VARCHAR(50),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_impedimento_fechamento_id ON tb_impedimento_fechamento(fechamento_id);
CREATE INDEX IF NOT EXISTS idx_impedimento_fechamento_comp ON tb_impedimento_fechamento(competencia);
CREATE INDEX IF NOT EXISTS idx_impedimento_fechamento_matr ON tb_impedimento_fechamento(matricula);
CREATE INDEX IF NOT EXISTS idx_impedimento_fechamento_marca ON tb_impedimento_fechamento(cod_marca);
