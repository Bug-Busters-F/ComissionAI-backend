package com.bugbusters.backend.dto.apuracao;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Parâmetros para execução do cálculo definitivo de comissões por campanha")
public record CalculoDefinitivoCampanhaRequest(
        @Schema(description = "Competência opcional no formato YYYY-MM", example = "2026-05")
        String competencia,

        @Schema(description = "Data inicial de corte para apuração", example = "2026-05-01")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dataInicio,

        @Schema(description = "Data final de corte para apuração", example = "2026-05-31")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dataFim,

        @Schema(description = "Identificação do usuário ou sistema executor", example = "gestor.campanhas")
        String executadoPor,

        @Schema(description = "Observações ou justificativas para a apuração definitiva", example = "Fechamento mensal da campanha")
        String observacoes,

        @Schema(description = "Indica se deve forçar o recálculo mesmo se a competência tiver fechamento prévio", example = "false")
        Boolean forcarRecalculo
) {
    public CalculoDefinitivoCampanhaRequest {
        if (executadoPor == null || executadoPor.isBlank()) {
            executadoPor = "SISTEMA";
        }
        if (forcarRecalculo == null) {
            forcarRecalculo = false;
        }
    }
}

