package com.bugbusters.backend.dto.relatorio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Consolidação de fechamento financeiro individual por colaborador/vendedor")
public record ConsolidadoColaboradorDTO(
        @Schema(description = "Matrícula funcional do colaborador", example = "1001")
        String matricula,

        @Schema(description = "Código do cargo do colaborador")
        Integer codCargo,

        @Schema(description = "Descrição do cargo")
        String nomeCargo,

        @Schema(description = "Código da loja")
        Integer codLoja,

        @Schema(description = "Nome descritivo da loja")
        String nomeLoja,

        @Schema(description = "Código da marca")
        Integer codMarca,

        @Schema(description = "Nome descritivo da marca")
        String nomeMarca,

        @Schema(description = "Quantidade de vendas computadas")
        int quantidadeVendas,

        @Schema(description = "Valor total bruto de vendas do colaborador (R$)")
        BigDecimal valorTotalVendas,

        @Schema(description = "Valor total de comissões apuradas (R$)")
        BigDecimal valorTotalComissao,

        @Schema(description = "Valor de bônus percentual apurado (R$)")
        BigDecimal valorTotalBonusPercentual,

        @Schema(description = "Valor de bônus fixo apurado (R$)")
        BigDecimal valorTotalBonusFixo,

        @Schema(description = "Valor total de bônus apurados (R$)")
        BigDecimal valorTotalBonus,

        @Schema(description = "Valor total líquido a pagar ao colaborador (R$)")
        BigDecimal valorTotalAPagar,

        @Schema(description = "Taxa média efetiva individual (decimal)")
        BigDecimal taxaMediaEfetiva
) {}

