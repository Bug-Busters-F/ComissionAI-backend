package com.bugbusters.backend.dto.cobertura;

import com.bugbusters.backend.dto.basecomiss.ReferenciaFaltanteDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Diagnóstico da cobertura de Taxas Base de Comissão para a competência")
public record CoberturaTaxasDiagnosticoDTO(
        @Schema(description = "Indica se todas as taxas aplicáveis existem para a competência", example = "true")
        boolean coberto,

        @Schema(description = "Status resumido (COBERTO, FALTA_ANEXO, TAXA_FALTANTE, SEM_VENDAS_ELEGIVEIS, SEM_BASE_NA_COMPETENCIA)", example = "COBERTO")
        String status,

        @Schema(description = "Report IDs dos lotes de comissão anexados à campanha", example = "[10030]")
        List<Long> lotesAnexadosReportIds,

        @Schema(description = "Lotes de comissão concluídos na competência ainda não anexados à campanha")
        List<LoteDisponivelParaAnexoDTO> lotesDisponiveisNaoAnexados,

        @Schema(description = "Total de pares distintos (marca/cargo) exigidos pelas vendas elegíveis", example = "4")
        int totalParesExigidos,

        @Schema(description = "Lista de pares (marca/cargo) sem taxa base cadastrada na competência")
        List<ReferenciaFaltanteDTO> referenciasFaltantes,

        @Schema(description = "Resumo agregado de pendências dos lotes de comissões da competência")
        ResumoPendenciasCoberturaDTO resumoPendencias,

        @Schema(description = "Mensagem orientadora", example = "Todos os 4 pares possuem taxa base vigente.")
        String mensagem
) {
}
