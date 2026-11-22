package com.bugbusters.backend.dto.relatorio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Consolidação de fechamento financeiro agrupada por equipe (Loja e Marca)")
public record ConsolidadoEquipeDTO(
        @Schema(description = "Código da loja")
        Integer codLoja,

        @Schema(description = "Nome descritivo da loja")
        String nomeLoja,

        @Schema(description = "Código da marca")
        Integer codMarca,

        @Schema(description = "Nome descritivo da marca")
        String nomeMarca,

        @Schema(description = "Quantidade de vendas da equipe")
        int quantidadeVendas,

        @Schema(description = "Quantidade de colaboradores na equipe com vendas")
        int quantidadeColaboradores,

        @Schema(description = "Valor total bruto de vendas da equipe (R$)")
        BigDecimal valorTotalVendas,

        @Schema(description = "Valor total de comissões apuradas para a equipe (R$)")
        BigDecimal valorTotalComissao,

        @Schema(description = "Valor total de bônus apurados para a equipe (R$)")
        BigDecimal valorTotalBonus,

        @Schema(description = "Taxa média efetiva da equipe (decimal)")
        BigDecimal taxaMediaEfetiva,

        @Schema(description = "Participação percentual sobre o total de comissões")
        BigDecimal percentualSobreTotalComissao
) {}

