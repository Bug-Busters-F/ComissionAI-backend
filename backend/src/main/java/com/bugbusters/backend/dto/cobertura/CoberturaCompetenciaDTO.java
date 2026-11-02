package com.bugbusters.backend.dto.cobertura;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Diagnóstico consolidado de uma competência que intersecta a vigência da campanha")
public record CoberturaCompetenciaDTO(
        @Schema(description = "Competência no formato YYYY-MM", example = "2026-07")
        String competencia,

        @Schema(description = "Início do recorte de vigência da campanha neste mês", example = "2026-07-15")
        LocalDate inicioRecorteVigencia,

        @Schema(description = "Fim do recorte de vigência da campanha neste mês", example = "2026-07-31")
        LocalDate fimRecorteVigencia,

        @Schema(description = "Indica se o mês possui recorte parcial de datas", example = "true")
        boolean mesParcial,

        @Schema(description = "Indica se a competência está plenamente coberta para fechamento", example = "true")
        boolean coberta,

        @Schema(description = "Códigos de bloqueios impeditivos nesta competência", example = "[\"FALTA_TAXA_BASE\"]")
        List<String> bloqueios,

        @Schema(description = "Total de pendências não aceitas nos lotes que cobrem esta competência", example = "0")
        long totalPendenciasNaoAceitas,

        @Schema(description = "Diagnóstico de RH na competência")
        CoberturaRhDiagnosticoDTO rh,

        @Schema(description = "Diagnóstico de Vendas na competência")
        CoberturaVendasDiagnosticoDTO vendas,

        @Schema(description = "Diagnóstico de Taxas Base na competência")
        CoberturaTaxasDiagnosticoDTO taxasBase
) {
}
