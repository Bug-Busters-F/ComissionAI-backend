ALTER TABLE tb_sales ADD COLUMN IF NOT EXISTS valor_venda numeric(15,2);
UPDATE tb_sales SET valor_venda = 0 WHERE valor_venda IS NULL;
ALTER TABLE tb_sales ALTER COLUMN valor_venda SET NOT NULL;
