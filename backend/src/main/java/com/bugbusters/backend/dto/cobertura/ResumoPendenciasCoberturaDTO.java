package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Resumo agregado de pendências/inconsistências de lotes")
public record ResumoPendenciasCoberturaDTO(
        @Schema(description = "Total de inconsistências geradas nos lotes analisados", example = "3")
        long total,

        @Schema(description = "Total de inconsistências que ainda não possuem aceite", example = "1")
        long totalNaoAceitas,

        @Schema(description = "Contagem agrupada por severidade (IMPEDITIVO, ADVERTENCIA, AVISO)", example = "{\"ADVERTENCIA\": 1}")
        Map<String, Long> porSeveridade,

        @Schema(description = "Contagem agrupada por campo inconsistente", example = "{\"matricula\": 1}")
        Map<String, Long> porCampo
) {
    public static ResumoPendenciasCoberturaDTO vazio() {
        return new ResumoPendenciasCoberturaDTO(0L, 0L, Map.of(), Map.of());
    }
}
