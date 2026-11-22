package com.bugbusters.backend.dto.relatorio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Consolidação de fechamento financeiro agrupada por canal de venda")
public record ConsolidadoCanalDTO(
        @Schema(description = "Canal de venda", example = "ECOMMERCE")
        String canal,

        @Schema(description = "Quantidade de vendas no canal")
        int quantidadeVendas,

        @Schema(description = "Quantidade de colaboradores que venderam no canal")
        int quantidadeColaboradores,

        @Schema(description = "Valor total bruto de vendas no canal (R$)")
        BigDecimal valorTotalVendas,

        @Schema(description = "Valor total de comissões geradas no canal (R$)")
        BigDecimal valorTotalComissao,

        @Schema(description = "Valor total de bônus gerados no canal (R$)")
        BigDecimal valorTotalBonus,

        @Schema(description = "Taxa média efetiva praticada no canal (decimal)")
        BigDecimal taxaMediaEfetiva,

        @Schema(description = "Participação percentual sobre o volume total de vendas")
        BigDecimal percentualSobreTotalVendas,

        @Schema(description = "Participação percentual sobre o total de comissões geradas")
        BigDecimal percentualSobreTotalComissao
) {}

