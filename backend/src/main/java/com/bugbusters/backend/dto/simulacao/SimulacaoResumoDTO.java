package com.bugbusters.backend.dto.simulacao;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Resumo executivo de uma simulação persistida no sistema")
public record SimulacaoResumoDTO(
        @Schema(description = "Identificador único da simulação")
        UUID id,

        @Schema(description = "Protocolo de auditoria da simulação")
        UUID protocolo,

        @Schema(description = "Data e hora de execução da simulação")
        OffsetDateTime executadoEm,

        @Schema(description = "Competência histórica de referência", example = "2026-09")
        String competencia,

        @Schema(description = "Identificador da campanha vinculada (se houver)", example = "1")
        Long campanhaId,

        @Schema(description = "Título da campanha simulada", example = "Campanha Black Friday E-commerce")
        String tituloCampanha,

        @Schema(description = "Data de início da vigência hipotética da campanha", example = "2026-11-01")
        LocalDate dataInicioCampanha,

        @Schema(description = "Data de término da vigência hipotética da campanha", example = "2026-11-15")
        LocalDate dataFimCampanha,

        @Schema(description = "Dias inclusivos de duração da campanha", example = "15")
        long diasInclusivos,

        @Schema(description = "Fator multiplicador de projeção (dias ÷ 30)", example = "0.500000")
        BigDecimal fatorProjecao,

        @Schema(description = "Total de vendas pertencentes ao recorte", example = "400")
        int totalVendasRecortadas,

        @Schema(description = "Valor total monetário recortado a 100%", example = "2000000.00")
        BigDecimal valorTotalRecortado,

        @Schema(description = "Consolidado do Cenário 100% (Proposta Revisada)")
        CenarioConsolidadoDTO cenario100,

        @Schema(description = "Consolidado do Cenário 80% (Volume Reduzido)")
        CenarioConsolidadoDTO cenario80,

        @Schema(description = "Consolidado do Cenário 120% (Crescimento Otimista)")
        CenarioConsolidadoDTO cenario120,

        @Schema(description = "Consolidado do Cenário 150% (Alta Performance)")
        CenarioConsolidadoDTO cenario150
) {}
