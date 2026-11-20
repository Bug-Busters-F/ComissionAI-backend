package com.bugbusters.backend.dto.auditoria;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bloco consolidado de relacionamentos de um evento auditável com campanhas, arquivos e cálculos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Relacionamentos resolvidos do evento com campanhas, arquivos/lotes e cálculos")
public record AuditoriaRelacionamentosDTO(
        RelacionamentoCampanhaResumoDTO campanha,
        RelacionamentoArquivoResumoDTO arquivo,
        RelacionamentoCalculoResumoDTO calculo
) {
    public boolean vazio() {
        return campanha == null && arquivo == null && calculo == null;
    }
}
