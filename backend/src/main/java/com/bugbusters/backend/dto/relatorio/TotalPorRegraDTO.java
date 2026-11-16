package com.bugbusters.backend.dto.relatorio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Totais consolidados por regra ou origem de taxa aplicada (FRONT-11 / BACK-15)")
public record TotalPorRegraDTO(
        @Schema(description = "Identificador da regra de campanha (0 para taxa base)")
        Long idRegra,

        @Schema(description = "Nome da regra ou taxa contratual aplicada")
        String nomeRegra,

        @Schema(description = "Tipo de operação aplicada")
        String tipoOperacao,

        @Schema(description = "Quantidade de vendas liquidadas pela regra")
        long quantidadeVendas,

        @Schema(description = "Valor total bruto das vendas (R$)")
        BigDecimal valorTotalVendas,

        @Schema(description = "Valor total de comissões apuradas pela regra (R$)")
        BigDecimal valorTotalComissao
) {}
