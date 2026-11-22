package com.bugbusters.backend.dto.relatorio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Totais gerais consolidados do fechamento da competência (FRONT-11 / BACK-15)")
public record TotaisGeraisFechamentoDTO(
        @Schema(description = "Total de vendas processadas no fechamento")
        int totalVendasProcessadas,

        @Schema(description = "Total de vendas calculadas (conforme filtros aplicados)")
        int totalCalculados,

        @Schema(description = "Total de vendas com impedimento")
        int totalImpedimentos,

        @Schema(description = "Soma do valor bruto das vendas liquidadas (R$)")
        BigDecimal valorTotalVendas,

        @Schema(description = "Soma do valor total de comissões apuradas (R$)")
        BigDecimal valorTotalComissao
) {}
