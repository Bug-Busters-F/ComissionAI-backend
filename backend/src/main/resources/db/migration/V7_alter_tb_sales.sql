-- antes de colocar em src/main/resources/db/migration/

-- Remove a coluna reference_month (referencemonth), não é mais usada
ALTER TABLE tb_sales
    DROP COLUMN IF EXISTS referencemonth;

-- Garante que sale_date seja do tipo DATE (equivalente a java.time.LocalDate)
-- Ajuste "sale_date" para o nome real da coluna, caso seja diferente
ALTER TABLE tb_sales
    ALTER COLUMN sale_date TYPE DATE
    USING sale_date::DATE;