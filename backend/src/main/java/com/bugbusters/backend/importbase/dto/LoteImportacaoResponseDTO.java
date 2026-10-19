package com.bugbusters.backend.importbase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Detalhes cadastrais e de processamento de um lote de importação")
public record LoteImportacaoResponseDTO(
    @Schema(description = "ID do lote (JobExecution)", example = "123")
    Long reportId,

    @Schema(description = "Identificador UUID do lote", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    String idLote,

    @Schema(description = "Nome do arquivo importado", example = "vendas_outubro.xlsx")
    String nomeArquivo,

    @Schema(description = "Tipo de base importada", example = "SALES")
    String tipoBase,

    @Schema(description = "Competência associada no formato YYYY-MM", example = "2026-10", nullable = true)
    String competencia,

    @Schema(description = "Status atual do lote", example = "CONCLUIDO")
    String status,

    @Schema(description = "Total de registros processados", example = "100")
    int totalLinhas,

    @Schema(description = "Mensagem ou motivo de falha/exclusão", nullable = true)
    String mensagem,

    @Schema(description = "Data e hora de criação do lote")
    OffsetDateTime criadoEm,

    @Schema(description = "Data e hora de início do processamento", nullable = true)
    OffsetDateTime iniciadoEm,

    @Schema(description = "Data e hora de término do processamento", nullable = true)
    OffsetDateTime finalizadoEm,

    @Schema(description = "Data e hora de exclusão do lote", nullable = true)
    OffsetDateTime excluidoEm,

    @Schema(description = "Usuário responsável pela exclusão", nullable = true)
    String excluidoPor
) {}
