package com.bugbusters.backend.dto.simulacao;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados para aceite; autoria e obtida exclusivamente da sessao autenticada")
public record AceiteSimulacaoRequest(
        @Schema(description = "Campo legado ignorado") String aceitoPor,
        String motivo,
        Long campanhaId,
        Integer versaoEsperada
) {
    public AceiteSimulacaoRequest(String aceitoPor, String motivo) {
        this(aceitoPor, motivo, null, null);
    }
}
