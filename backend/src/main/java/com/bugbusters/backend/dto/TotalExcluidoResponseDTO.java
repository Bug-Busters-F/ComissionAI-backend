package com.bugbusters.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta padrão contendo a contagem de registros excluídos em operações em lote")
public record TotalExcluidoResponseDTO(
        @Schema(description = "Quantidade total de registros excluídos", example = "150")
        long totalExcluido
) {}
