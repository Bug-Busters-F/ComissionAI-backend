package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Diagnóstico da cobertura de Vendas para a competência")
public record CoberturaVendasDiagnosticoDTO(
        @Schema(description = "Indica se as vendas estão cobertas sem pendências bloqueantes", example = "true")
        boolean coberto,

        @Schema(description = "Status resumido (COBERTO, FALTA_ANEXO, COM_PENDENCIAS_NAO_ACEITAS, SEM_BASE_NA_COMPETENCIA)", example = "COBERTO")
        String status,

        @Schema(description = "Report IDs dos lotes de vendas anexados à campanha", example = "[10020]")
        List<Long> lotesAnexadosReportIds,

        @Schema(description = "Lotes de vendas concluídos na competência ainda não anexados à campanha")
        List<LoteDisponivelParaAnexoDTO> lotesDisponiveisNaoAnexados,

        @Schema(description = "Total de vendas cadastradas na competência", example = "420")
        long totalVendasNoMes,

        @Schema(description = "Total de vendas compreendidas no recorte exato da vigência", example = "215")
        long totalVendasNoRecorteVigencia,

        @Schema(description = "Total de vendas que atendem ao público-alvo das regras da campanha", example = "180")
        long totalVendasElegiveisPublico,

        @Schema(description = "Avisos de sobreposição de vendas entre lotes da competência")
        List<AvisoSobreposicaoVendaDTO> avisosSobreposicao,

        @Schema(description = "Resumo agregado de pendências dos lotes de vendas da competência")
        ResumoPendenciasCoberturaDTO resumoPendencias,

        @Schema(description = "Mensagem orientadora", example = "180 vendas elegíveis cobertas.")
        String mensagem
) {
}
