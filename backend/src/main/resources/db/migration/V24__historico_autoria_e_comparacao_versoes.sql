ALTER TABLE tb_campanha_versao ADD COLUMN versao_anterior_id BIGINT REFERENCES tb_campanha_versao(id);
ALTER TABLE tb_campanha_versao ADD COLUMN publicado_por VARCHAR(150);
ALTER TABLE tb_campanha_versao ADD COLUMN simulacao_aceita_id UUID REFERENCES tb_simulacao(id);
ALTER TABLE tb_regra ADD COLUMN identificador_logico VARCHAR(150);

UPDATE tb_campanha_versao atual
SET versao_anterior_id = (
    SELECT anterior.id FROM tb_campanha_versao anterior
    WHERE anterior.campanha_id = atual.campanha_id
      AND anterior.numero < atual.numero AND anterior.status = 'PUBLICADA'
    ORDER BY anterior.numero DESC LIMIT 1
);

-- Use only an accepted simulation belonging to this published version. The campaign's
-- current pointer may already refer to a different, unpublished proposal.
UPDATE tb_campanha_versao v
SET simulacao_aceita_id = aceita.id,
    publicado_por = aceita.aceito_por
FROM (
    SELECT DISTINCT ON (campanha_versao_id) id, campanha_versao_id, aceito_por
    FROM tb_simulacao
    WHERE status_aceite = 'ACEITA' AND campanha_versao_id IS NOT NULL
    ORDER BY campanha_versao_id, aceito_em DESC NULLS LAST, executado_em DESC, id
) aceita
WHERE aceita.campanha_versao_id = v.id AND v.status = 'PUBLICADA';

UPDATE tb_regra
SET identificador_logico = COALESCE(NULLIF(bloco_id, ''), 'legado-' || id::text)
WHERE identificador_logico IS NULL;

ALTER TABLE tb_regra ALTER COLUMN identificador_logico SET NOT NULL;
CREATE INDEX idx_regra_identificador_logico ON tb_regra(campanha_versao_id, identificador_logico);
CREATE INDEX idx_campanha_versao_publicada ON tb_campanha_versao(campanha_id, status, numero DESC);
