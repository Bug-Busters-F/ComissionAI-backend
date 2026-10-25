ALTER TABLE tb_regra ADD COLUMN rastreabilidade_json TEXT;
ALTER TABLE tb_campanha_versao ADD COLUMN ultima_etapa INTEGER NOT NULL DEFAULT 1 CHECK (ultima_etapa BETWEEN 1 AND 4);
ALTER TABLE tb_campanha_versao ADD COLUMN etapa_mais_avancada INTEGER NOT NULL DEFAULT 1 CHECK (etapa_mais_avancada BETWEEN 1 AND 4);
