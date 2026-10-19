package com.bugbusters.backend.importbase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Resultado da exclusão de um lote de importação")
public record ExclusaoLoteResponseDTO(
    @Schema(description = "Identificador público do relatório/lote", example = "123")
    long reportId,

    @Schema(description = "Status após a exclusão", example = "EXCLUIDO")
    String status,

    @Schema(description = "Quantidade de registros excluídos", example = "50")
    long registrosRemovidos,

    @Schema(description = "Data e hora da exclusão")
    OffsetDateTime excluidoEm
) {}
