package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Aviso não-bloqueante de venda com data fora do período de admissão/demissão do colaborador")
public record AvisoVigenciaContratualDTO(
        @Schema(description = "Matrícula do colaborador", example = "102030")
        String matricula,

        @Schema(description = "Nome do colaborador", example = "Carlos Silva")
        String nomeColaborador,

        @Schema(description = "Data de admissão", example = "2026-07-20")
        LocalDate dataAdmissao,

        @Schema(description = "Data de demissão (se houver)", example = "null")
        LocalDate dataDemissao,

        @Schema(description = "Data da venda associada", example = "2026-07-16")
        LocalDate dataVenda
) {
}
