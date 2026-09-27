package com.bugbusters.backend.dto.calculo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Resultado consolidado do cálculo de comissões por competência")
public record CalculoCompetenciaResponseDTO(
    @Schema(description = "Competência apurada (mês/ano)", example = "2026-09")
    String competencia,

    @Schema(description = "Total de vendas da competência processadas", example = "10")
    int totalVendasProcessadas,

    @Schema(description = "Total de vendas calculadas com sucesso", example = "8")
    int totalCalculados,

    @Schema(description = "Total de vendas com impedimento", example = "2")
    int totalImpedimentos,

    @Schema(description = "Soma do valor bruto das vendas calculadas com sucesso (R$)", example = "8000.00")
    BigDecimal valorTotalVendas,

    @Schema(description = "Soma do valor total de comissões apuradas (R$)", example = "800.00")
    BigDecimal valorTotalComissao,

    @Schema(description = "Lista dos cálculos efetuados com sucesso")
    List<CalculoItemResponseDTO> resultados,

    @Schema(description = "Lista das vendas que apresentaram impedimentos no cálculo")
    List<CalculoImpedimentoDTO> impedimentos
) {}
