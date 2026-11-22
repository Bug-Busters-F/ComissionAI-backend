-- =============================================================================
-- V45: Blindar unicidade de alertas estatísticos ativos por lote/assinatura,
--      padronizar grupo_comparavel legado (UUID -> códigos de negócio) e
--      corrigir autoria legada (SISTEMA -> efetivado_por) em cargas da preparação.
-- =============================================================================

-- 1. Limpar eventuais alertas ativos duplicados por (lote_preparacao_id, assinatura)
--    mantendo apenas o registro mais recente ativo antes de criar o índice único parcial.
WITH duplicados AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY lote_preparacao_id, assinatura
               ORDER BY gerado_em DESC, id DESC
           ) AS rn
    FROM tb_alerta_estatistico
    WHERE substituido_em IS NULL
)
UPDATE tb_alerta_estatistico a
SET substituido_em = CURRENT_TIMESTAMP
FROM duplicados d
WHERE a.id = d.id
  AND d.rn > 1;

-- 2. Índice único parcial: garante no banco que nunca existam dois alertas ativos
--    com a mesma assinatura para o mesmo lote de preparação.
CREATE UNIQUE INDEX IF NOT EXISTS uk_alerta_estatistico_ativo_lote_assinatura
    ON tb_alerta_estatistico (lote_preparacao_id, assinatura)
    WHERE substituido_em IS NULL;

-- 3. Padronizar grupo_comparavel de alertas antigos que ainda armazenam UUIDs
--    para códigos de negócio (AAAA-MM|loja=<code>|cargo=<code>|marca=<code>).
UPDATE tb_alerta_estatistico a
SET grupo_comparavel = TO_CHAR(s.sale_date, 'YYYY-MM')
    || '|loja=' || st.code
    || '|cargo=' || p.code
    || '|marca=' || b.code
FROM tb_sales s
JOIN tb_store st ON st.id = s.store_id
JOIN tb_registration r ON r.id = s.registration_id
JOIN tb_position p ON p.id = r.position_id
JOIN tb_brands b ON b.id = s.brand_id
WHERE a.venda_id = s.id
  AND a.grupo_comparavel ~ 'loja=[0-9a-fA-F-]{36}';

-- 4. Backfill de autoria (criado_por / perfil_criador) para lotes efetivados antes da V44
--    que receberam DEFAULT 'SISTEMA' mas possuem efetivado_por em tb_lote_preparacao.
UPDATE tb_envio_arquivo e
SET criado_por = TRIM(lp.efetivado_por),
    perfil_criador = COALESCE(
        NULLIF(TRIM(e.perfil_criador), ''),
        (SELECT u.papel FROM tb_usuario u WHERE LOWER(u.email) = LOWER(TRIM(lp.efetivado_por)) LIMIT 1)
    )
FROM tb_lote_preparacao lp
WHERE lp.envio_arquivo_efetivado_id = e.id
  AND UPPER(COALESCE(TRIM(e.criado_por), 'SISTEMA')) = 'SISTEMA'
  AND lp.efetivado_por IS NOT NULL
  AND TRIM(lp.efetivado_por) <> ''
  AND UPPER(TRIM(lp.efetivado_por)) <> 'SISTEMA';

UPDATE tb_auditoria_geral ag
SET usuario = TRIM(lp.efetivado_por),
    perfil_usuario = COALESCE(
        NULLIF(NULLIF(TRIM(ag.perfil_usuario), ''), 'SISTEMA'),
        (SELECT u.papel FROM tb_usuario u WHERE LOWER(u.email) = LOWER(TRIM(lp.efetivado_por)) LIMIT 1),
        ag.perfil_usuario
    )
FROM tb_lote_preparacao lp
WHERE ag.lote_preparacao_id = lp.id
  AND UPPER(COALESCE(TRIM(ag.usuario), 'SISTEMA')) = 'SISTEMA'
  AND lp.efetivado_por IS NOT NULL
  AND TRIM(lp.efetivado_por) <> ''
  AND UPPER(TRIM(lp.efetivado_por)) <> 'SISTEMA';

