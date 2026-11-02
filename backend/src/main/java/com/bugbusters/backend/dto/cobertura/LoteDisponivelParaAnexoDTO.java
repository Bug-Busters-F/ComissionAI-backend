package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Resumo de lote geral disponível na competência para anexar à campanha")
public record LoteDisponivelParaAnexoDTO(
        @Schema(description = "Report ID do lote", example = "10042")
        Long reportId,

        @Schema(description = "UUID do lote de importação", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        String idLote,

        @Schema(description = "Tipo de base (HR, SALES, COMISSIONS)", example = "SALES")
        String tipoBase,

        @Schema(description = "Competência no formato YYYY-MM (nula para HR)", example = "2026-08")
        String competencia,

        @Schema(description = "Nome original do arquivo importado", example = "vendas_agosto_2026.xlsx")
        String nomeArquivoOriginal,

        @Schema(description = "Total de linhas válidas importadas", example = "1250")
        int linhasValidas,

        @Schema(description = "Total de inconsistências no lote", example = "0")
        long totalInconsistencias,

        @Schema(description = "Data/hora de conclusão da importação", example = "2026-08-31T18:00:00Z")
        OffsetDateTime finalizadoEm
) {
}
