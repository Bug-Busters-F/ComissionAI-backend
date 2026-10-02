-- =============================================================================
-- V4: Criação das tabelas de suporte à importação de bases de dados
-- =============================================================================

CREATE TABLE IF NOT EXISTS tb_brands (
    id UUID PRIMARY KEY,
    code INTEGER NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_store (
    id UUID PRIMARY KEY,
    code INTEGER NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_position (
    id UUID PRIMARY KEY,
    code INTEGER NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_registration (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES tb_store(id),
    position_id UUID NOT NULL REFERENCES tb_position(id),
    registration VARCHAR(255) NOT NULL UNIQUE,
    admiss_date DATE NOT NULL,
    demiss_date DATE
);

CREATE TABLE IF NOT EXISTS tb_basecomiss (
    id UUID PRIMARY KEY,
    brand_id UUID NOT NULL REFERENCES tb_brands(id),
    position_id UUID NOT NULL REFERENCES tb_position(id),
    percentage NUMERIC(6, 4) NOT NULL,
    reference_month DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_sales (
    id UUID PRIMARY KEY,
    registration_id UUID NOT NULL REFERENCES tb_registration(id),
    store_id UUID NOT NULL REFERENCES tb_store(id),
    brand_id UUID NOT NULL REFERENCES tb_brands(id),
    valor_venda NUMERIC(15, 2) NOT NULL,
    sale_date DATE NOT NULL,
    canal VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
