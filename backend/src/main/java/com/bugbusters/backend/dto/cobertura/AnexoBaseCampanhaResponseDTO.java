package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Dados do anexo de base vinculado à campanha")
public record AnexoBaseCampanhaResponseDTO(
        @Schema(description = "Identificador único do anexo", example = "9f8e7d6c-5b4a-3210-fedc-ba0987654321")
        UUID id,

        @Schema(description = "Identificador da campanha", example = "15")
        Long campanhaId,

        @Schema(description = "Report ID do lote de importação", example = "10042")
        Long reportId,

        @Schema(description = "UUID do lote de importação", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        String idLote,

        @Schema(description = "Tipo de base (HR, SALES, COMISSIONS)", example = "SALES")
        String tipoBase,

        @Schema(description = "Competência no formato YYYY-MM (nula para HR)", example = "2026-08")
        String competencia,

        @Schema(description = "Nome original do arquivo importado", example = "vendas_agosto_2026.xlsx")
        String nomeArquivoOriginal,

        @Schema(description = "Status do lote de importação", example = "CONCLUIDO")
        String statusLote,

        @Schema(description = "Total de linhas válidas importadas", example = "1250")
        int linhasValidas,

        @Schema(description = "Total de inconsistências geradas", example = "0")
        long totalInconsistencias,

        @Schema(description = "Data/hora em que o lote foi anexado", example = "2026-09-01T14:30:00-03:00")
        OffsetDateTime anexadoEm,

        @Schema(description = "Responsável pela associação", example = "maria.analista@empresa.com")
        String anexadoPor
) {
}
