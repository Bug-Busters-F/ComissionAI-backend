package com.bugbusters.backend.dto.indicador;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Contrato de leitura para os cartões e a fila de trabalho do painel. */
public record IndicadoresResponse(
                OffsetDateTime atualizadoEm,
                String perfil,
                Map<String, Long> competencias,
                Map<String, Long> campanhas,
                Map<String, Long> pendencias,
                Map<String, Long> alertas,
                List<ItemAguardandoAcao> aguardandoAcao) {

        public record ItemAguardandoAcao(
                        String tipo,
                        String descricao,
                        Long campanhaId,
                        String competencia,
                        String href,
                        String metodo) {
        }
}
