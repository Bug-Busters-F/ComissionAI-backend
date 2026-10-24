CREATE TABLE tb_campanha_versao (
    id BIGSERIAL PRIMARY KEY,
    campanha_id BIGINT NOT NULL REFERENCES tb_campanha(id),
    numero INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    texto_original TEXT NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicado_em TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_campanha_versao_numero UNIQUE (campanha_id, numero)
);
ALTER TABLE tb_campanha ADD COLUMN versao_vigente_id BIGINT REFERENCES tb_campanha_versao(id);
ALTER TABLE tb_campanha ADD COLUMN versao_proposta_id BIGINT REFERENCES tb_campanha_versao(id);
ALTER TABLE tb_campanha ADD COLUMN versao_controle BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tb_regra ADD COLUMN campanha_versao_id BIGINT REFERENCES tb_campanha_versao(id);
ALTER TABLE tb_simulacao ADD COLUMN campanha_versao_id BIGINT REFERENCES tb_campanha_versao(id);

INSERT INTO tb_campanha_versao (campanha_id, numero, status, titulo, texto_original, data_inicio, data_fim, criado_em, publicado_em)
SELECT id, 1, CASE WHEN estado = 'ATIVA' THEN 'PUBLICADA' ELSE 'PROPOSTA' END,
       titulo, texto_original, data_inicio, data_fim, criado_em,
       CASE WHEN estado = 'ATIVA' THEN atualizado_em ELSE NULL END
FROM tb_campanha;
UPDATE tb_campanha c SET versao_vigente_id = v.id
FROM tb_campanha_versao v WHERE v.campanha_id = c.id AND v.numero = 1 AND c.estado = 'ATIVA';
UPDATE tb_campanha c SET versao_proposta_id = v.id
FROM tb_campanha_versao v WHERE v.campanha_id = c.id AND v.numero = 1 AND c.estado <> 'ATIVA';
UPDATE tb_regra r SET campanha_versao_id = v.id FROM tb_campanha_versao v WHERE v.campanha_id = r.campanha_id AND v.numero = 1;
CREATE INDEX idx_regra_campanha_versao ON tb_regra(campanha_versao_id);
CREATE INDEX idx_simulacao_campanha_versao ON tb_simulacao(campanha_versao_id);
