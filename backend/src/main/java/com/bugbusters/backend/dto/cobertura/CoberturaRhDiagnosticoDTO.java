package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Diagnóstico da cobertura de RH para a competência")
public record CoberturaRhDiagnosticoDTO(
        @Schema(description = "Indica se o RH está plenamente coberto", example = "true")
        boolean coberto,

        @Schema(description = "Status resumido (COBERTO, FALTA_ANEXO, COLABORADOR_SEM_CARGO, PENDENCIA_MATRICULA_EM_VENDAS, SEM_BASE_HR)", example = "COBERTO")
        String status,

        @Schema(description = "Report IDs dos lotes de RH anexados à campanha", example = "[10010]")
        List<Long> lotesAnexadosReportIds,

        @Schema(description = "Lotes de RH concluídos no sistema ainda não anexados à campanha")
        List<LoteDisponivelParaAnexoDTO> lotesDisponiveisNaoAnexados,

        @Schema(description = "Total de colaboradores distintos com vendas no mês", example = "42")
        long totalColaboradoresComVendasNoMes,

        @Schema(description = "Matrículas de colaboradores com vendas mas sem cargo cadastrado", example = "[]")
        List<String> colaboradoresSemCargo,

        @Schema(description = "Quantidade de pendências não aceitas de matrícula não cadastrada registradas nos lotes de vendas", example = "0")
        long pendenciasMatriculaReferenciadasEmVendas,

        @Schema(description = "Avisos informativos sobre datas de admissão/demissão fora da data da venda")
        List<AvisoVigenciaContratualDTO> avisosVigenciaContratual,

        @Schema(description = "Resumo de inconsistências nos lotes de RH anexados")
        ResumoPendenciasCoberturaDTO resumoPendenciasLotesRh,

        @Schema(description = "Mensagem orientadora", example = "Cobertura de RH validada.")
        String mensagem
) {
}
