package com.bugbusters.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Limpa somente fixtures de campanha, respeitando as referências circulares e o histórico de versões. */
public final class CampaignDataCleanup {
    private CampaignDataCleanup() {}

    public static void clear(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM tb_alerta_estatistico");
        jdbc.update("UPDATE tb_campanha SET simulacao_aceita_id = NULL, versao_vigente_id = NULL, versao_proposta_id = NULL");
        jdbc.update("UPDATE tb_campanha_versao SET simulacao_aceita_id = NULL, versao_anterior_id = NULL");
        jdbc.update("UPDATE tb_lote_preparacao SET lote_preparacao_anterior_id = NULL, envio_arquivo_origem_id = NULL, envio_arquivo_efetivado_id = NULL");
        jdbc.update("UPDATE tb_resultado_calculo SET lote_correcao_id = NULL");
        jdbc.update("DELETE FROM tb_propagacao_impacto_base");
        jdbc.update("DELETE FROM tb_snapshot_resultado_calculo");
        jdbc.update("DELETE FROM tb_historico_versao_registro");
        jdbc.update("DELETE FROM tb_historico_edicao_preparacao");
        jdbc.update("DELETE FROM tb_linha_preparacao");
        jdbc.update("DELETE FROM tb_lote_preparacao");
        jdbc.update("DELETE FROM tb_simulacao_venda");
        jdbc.update("DELETE FROM tb_simulacao");
        jdbc.update("DELETE FROM tb_regra");
        jdbc.update("DELETE FROM tb_campanha_anexo_base");
        jdbc.update("DELETE FROM tb_campanha_versao");
        jdbc.update("DELETE FROM tb_campanha");
    }
}
