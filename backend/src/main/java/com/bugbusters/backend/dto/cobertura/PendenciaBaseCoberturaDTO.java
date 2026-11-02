package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Detalhe de pendência/inconsistência de lote para paginação")
public record PendenciaBaseCoberturaDTO(
        @Schema(description = "Identificador único da inconsistência", example = "501")
        Long inconsistenciaId,

        @Schema(description = "Report ID do lote de origem", example = "10020")
        Long reportId,

        @Schema(description = "UUID do lote de importação", example = "lote-vendas-2026-07")
        String idLote,

        @Schema(description = "Tipo de base do lote", example = "SALES")
        String tipoBase,

        @Schema(description = "Competência do lote", example = "2026-07")
        String competencia,

        @Schema(description = "Número da linha no arquivo original", example = "45")
        int linha,

        @Schema(description = "Campo que gerou inconsistência", example = "matricula")
        String campo,

        @Schema(description = "Motivo descritivo da inconsistência", example = "Matrícula não cadastrada: 998877")
        String motivo,

        @Schema(description = "Severidade (IMPEDITIVO, ADVERTENCIA, AVISO)", example = "ADVERTENCIA")
        String severidade,

        @Schema(description = "Indica se a inconsistência exige aceite para liberação do fechamento", example = "true")
        boolean exigeAceite,

        @Schema(description = "Indica se a inconsistência já foi aceita via BACK-7", example = "false")
        boolean aceita,

        @Schema(description = "Usuário responsável pelo aceite", example = "gestor@empresa.com")
        String aceitaPor,

        @Schema(description = "Data/hora do aceite", example = "2026-09-01T15:00:00Z")
        OffsetDateTime aceitaEm
) {
}
