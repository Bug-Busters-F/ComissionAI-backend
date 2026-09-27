ALTER TABLE tb_resultado_calculo
    ADD COLUMN IF NOT EXISTS origem_taxa VARCHAR(30);

ALTER TABLE tb_log_calculo_imutavel
    ADD COLUMN IF NOT EXISTS origem_taxa VARCHAR(30);
