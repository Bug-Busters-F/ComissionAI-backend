CREATE TABLE IF NOT EXISTS tb_alerta_estatistico (
    id UUID PRIMARY KEY,
    lote_preparacao_id UUID NOT NULL REFERENCES tb_lote_preparacao(id) ON DELETE RESTRICT,
    venda_id UUID REFERENCES tb_sales(id) ON DELETE SET NULL,
    matricula VARCHAR(100),
    data_referencia DATE,
    tipo VARCHAR(40) NOT NULL,
    metodo VARCHAR(40) NOT NULL,
    grupo_comparavel VARCHAR(300) NOT NULL,
    tamanho_amostra INTEGER NOT NULL,
    valor_observado NUMERIC(15, 2),
    media_grupo NUMERIC(15, 2),
    desvio_padrao NUMERIC(15, 4),
    score NUMERIC(15, 6),
    versao_bases VARCHAR(64) NOT NULL,
    assinatura VARCHAR(64) NOT NULL,
    gerado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    substituido_em TIMESTAMP WITH TIME ZONE,
    substituido_por_id UUID REFERENCES tb_alerta_estatistico(id) ON DELETE SET NULL,
    CONSTRAINT chk_alerta_estatistico_tipo CHECK (tipo IN ('OUTLIER_VENDA', 'AMOSTRA_INSUFICIENTE', 'SEM_VENDAS_PARA_RH'))
);

CREATE INDEX IF NOT EXISTS idx_alerta_estatistico_consulta
    ON tb_alerta_estatistico (substituido_em, data_referencia DESC, matricula);
CREATE INDEX IF NOT EXISTS idx_alerta_estatistico_lote
    ON tb_alerta_estatistico (lote_preparacao_id, substituido_em);
CREATE INDEX IF NOT EXISTS idx_alerta_estatistico_assinatura
    ON tb_alerta_estatistico (lote_preparacao_id, assinatura, substituido_em);
