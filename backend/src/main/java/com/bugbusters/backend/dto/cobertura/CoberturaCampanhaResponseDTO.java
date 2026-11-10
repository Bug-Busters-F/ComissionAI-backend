package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Diagnóstico consolidado de cobertura da campanha para o fechamento")
public record CoberturaCampanhaResponseDTO(
        @Schema(description = "Identificador da campanha", example = "15")
        Long campanhaId,

        @Schema(description = "Título da campanha", example = "Campanha Inverno 2026")
        String tituloCampanha,

        @Schema(description = "Estado da campanha (ACTIVE, DRAFT, etc.)", example = "ACTIVE")
        String estadoCampanha,

        @Schema(description = "Número da versão vigente analisada", example = "2")
        Integer versaoVigenteNumero,

        @Schema(description = "Data de início da versão vigente", example = "2026-07-15")
        LocalDate dataInicio,

        @Schema(description = "Data de fim da versão vigente", example = "2026-08-10")
        LocalDate dataFim,

        @Schema(description = "Indica se a campanha está pronta para fechamento", example = "true")
        boolean prontaParaFechamento,

        @Schema(description = "Total de competências que intersectam a vigência", example = "2")
        int totalCompetencias,

        @Schema(description = "Total de competências totalmente cobertas", example = "2")
        int competenciasCobertas,

        @Schema(description = "Total de competências com pendências ou faltas", example = "0")
        int competenciasPendentes,

        @Schema(description = "Resumo dos motivos de impedimento que bloqueiam o fechamento")
        List<String> resumoImpedimentos,

        @Schema(description = "Lista dos anexos de base ativos vinculados à campanha")
        List<AnexoBaseCampanhaResponseDTO> anexosAtivos,

        @Schema(description = "Detalhamento mês a mês da cobertura")
        List<CoberturaCompetenciaDTO> competencias
) {
}
