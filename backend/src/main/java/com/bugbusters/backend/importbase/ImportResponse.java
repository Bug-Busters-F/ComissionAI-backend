package com.bugbusters.backend.importbase;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Status e resultado de uma importação assíncrona de planilha Excel")
public record ImportResponse(
    @Schema(description = "ID da execução usado para consultar o relatório", example = "123")
    long reportId,
    @Schema(description = "Nome do arquivo submetido", example = "BASE_COMMISS_FINAL.xlsx")
    String nomeArquivo,
    @Schema(description = "Tipo de base", example = "COMISSIONS")
    ImportType tipoBase,
    @Schema(description = "Estado da importação", example = "PENDENTE")
    ImportStatus status,
    @Schema(description = "Registros confirmados: null durante o processamento e zero em caso de falha", example = "30", nullable = true)
    Long totalLinhas,
    @Schema(description = "Motivo da falha, quando disponível", nullable = true)
    String mensagem
) {}
