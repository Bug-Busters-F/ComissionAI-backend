package com.bugbusters.backend.importbase.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados opcionais para a exclusão do lote")
public record ExclusaoLoteRequestDTO(
    @Schema(description = "Motivo da exclusão ou cancelamento do lote", example = "Planilha enviada com valores incorretos")
    String motivo,

    @Schema(description = "Usuário solicitante da exclusão", example = "admin")
    String usuario
) {}
