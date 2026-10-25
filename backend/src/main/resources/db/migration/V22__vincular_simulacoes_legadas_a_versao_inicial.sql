-- V21 vinculou regras legadas, mas deixou as simulações existentes sem versão.
-- Preserva o aceite e o snapshot originais; a conferência continua exigindo hash e campos correspondentes.
UPDATE tb_simulacao s
SET campanha_versao_id = v.id
FROM tb_campanha_versao v
WHERE s.campanha_versao_id IS NULL
  AND s.campanha_id = v.campanha_id
  AND v.numero = 1;
