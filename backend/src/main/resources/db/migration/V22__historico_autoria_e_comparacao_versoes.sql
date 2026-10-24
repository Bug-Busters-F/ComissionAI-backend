ALTER TABLE tb_campanha_versao ADD COLUMN versao_anterior_id BIGINT REFERENCES tb_campanha_versao(id);
ALTER TABLE tb_campanha_versao ADD COLUMN publicado_por VARCHAR(150);
ALTER TABLE tb_campanha_versao ADD COLUMN simulacao_aceita_id UUID REFERENCES tb_simulacao(id);
ALTER TABLE tb_regra ADD COLUMN identificador_logico VARCHAR(150);

UPDATE tb_campanha_versao atual
SET versao_anterior_id = anterior.id
FROM tb_campanha_versao anterior
WHERE anterior.campanha_id = atual.campanha_id
  AND anterior.numero = atual.numero - 1;

UPDATE tb_campanha_versao v
SET simulacao_aceita_id = c.simulacao_aceita_id,
    publicado_por = s.aceito_por
FROM tb_campanha c
LEFT JOIN tb_simulacao s ON s.id = c.simulacao_aceita_id
WHERE c.versao_vigente_id = v.id;

UPDATE tb_regra
SET identificador_logico = COALESCE(NULLIF(bloco_id, ''), 'legado-' || id::text)
WHERE identificador_logico IS NULL;

ALTER TABLE tb_regra ALTER COLUMN identificador_logico SET NOT NULL;
CREATE INDEX idx_regra_identificador_logico ON tb_regra(campanha_versao_id, identificador_logico);
CREATE INDEX idx_campanha_versao_publicada ON tb_campanha_versao(campanha_id, status, numero DESC);
