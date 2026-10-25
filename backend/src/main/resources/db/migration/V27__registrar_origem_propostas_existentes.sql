-- Preserve the published source for proposals created before explicit edit sessions.
UPDATE tb_campanha_versao v
SET versao_anterior_id = c.versao_vigente_id
FROM tb_campanha c
WHERE c.versao_proposta_id = v.id
  AND v.status = 'PROPOSTA'
  AND v.versao_anterior_id IS NULL
  AND c.versao_vigente_id IS NOT NULL;
