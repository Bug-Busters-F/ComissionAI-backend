package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Requisição para associar uma base geral à campanha")
public record AnexarBaseCampanhaRequestDTO(
        @Schema(description = "Identificador reportId do lote de importação", example = "10042")
        @NotNull(message = "O reportId do lote é obrigatório.")
        Long reportId,

        @Schema(description = "Responsável pela associação (paliativo provisório até autenticação BACK-1)", example = "maria.analista@empresa.com")
        String responsavel
) {
}
