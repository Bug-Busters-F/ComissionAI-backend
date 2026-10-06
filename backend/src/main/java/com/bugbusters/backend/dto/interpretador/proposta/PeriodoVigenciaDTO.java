package com.bugbusters.backend.dto.interpretador.proposta;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Período de vigência temporal da proposta")
public record PeriodoVigenciaDTO(
        @Schema(description = "Data de início da vigência", example = "2026-10-01")
        LocalDate dataInicio,

        @Schema(description = "Data de término da vigência", example = "2026-10-31")
        LocalDate dataFim
) {
    public static PeriodoVigenciaDTO de(LocalDate dataInicio, LocalDate dataFim) {
        return new PeriodoVigenciaDTO(dataInicio, dataFim);
    }
}
