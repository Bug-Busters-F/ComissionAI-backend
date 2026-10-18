package com.bugbusters.backend.dto.simulacao;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Resultado completo da simulação de cenários, sensibilidade e projeções pro-rata")
public record SimulacaoCenariosResponse(
        @Schema(description = "Competência histórica de referência utilizada", example = "2026-09")
        String competencia,

        @Schema(description = "Total de vendas do público-alvo recortado", example = "400")
        int totalVendasRecortadas,

        @Schema(description = "Valor financeiro total do público-alvo recortado a 100%", example = "2000000.00")
        BigDecimal valorTotalRecortado,

        @Schema(description = "Identificador da campanha vinculada (se aplicável)", example = "1")
        Long campanhaId,

        @Schema(description = "Título da campanha simulada", example = "Campanha Black Friday E-commerce")
        String tituloCampanha,

        @Schema(description = "Data inicial considerada para a projeção da campanha", example = "2026-11-01")
        LocalDate dataInicioCampanha,

        @Schema(description = "Data final considerada para a projeção da campanha", example = "2026-11-15")
        LocalDate dataFimCampanha,

        @Schema(description = "Dias inclusivos de duração da campanha", example = "15")
        long diasInclusivos,

        @Schema(description = "Fator multiplicador de projeção mensal (dias ÷ 30)", example = "0.500000")
        BigDecimal fatorProjecao,

        @Schema(description = "Consolidado do Cenário 100% (Proposta Revisada)")
        CenarioConsolidadoDTO cenario100,

        @Schema(description = "Consolidado do Cenário 80% (Volume Reduzido)")
        CenarioConsolidadoDTO cenario80,

        @Schema(description = "Consolidado do Cenário 120% (Crescimento Otimista)")
        CenarioConsolidadoDTO cenario120,

        @Schema(description = "Consolidado do Cenário 150% (Alta Performance)")
        CenarioConsolidadoDTO cenario150,

        @Schema(description = "Quantidade de vendas detalhadas retornadas nesta página", example = "500")
        int totalVendasPaginadas,

        @Schema(description = "Detalhamento individual das vendas simuladas")
        List<VendaSimuladaDetalheDTO> vendas,

        @Schema(description = "Avisos ou alertas consolidados da simulação")
        List<String> avisosGerais
) {}
