package com.bugbusters.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Limpa somente fixtures de campanha, respeitando as referências circulares e o histórico de versões. */
public final class CampaignDataCleanup {
    private CampaignDataCleanup() {}

    public static void clear(JdbcTemplate jdbc) {
        jdbc.update("UPDATE tb_campanha SET simulacao_aceita_id = NULL, versao_vigente_id = NULL, versao_proposta_id = NULL");
        jdbc.update("UPDATE tb_campanha_versao SET simulacao_aceita_id = NULL, versao_anterior_id = NULL");
        jdbc.update("DELETE FROM tb_simulacao_venda");
        jdbc.update("DELETE FROM tb_simulacao");
        jdbc.update("DELETE FROM tb_regra");
        jdbc.update("DELETE FROM tb_campanha_versao");
        jdbc.update("DELETE FROM tb_campanha");
    }
}
