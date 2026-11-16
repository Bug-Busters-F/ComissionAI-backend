package com.bugbusters.backend.dto.relatorio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Totais consolidados por colaborador no relatório de fechamento (FRONT-11 / BACK-15)")
public record TotalPorColaboradorDTO(
        @Schema(description = "Matrícula funcional do colaborador")
        String matricula,

        @Schema(description = "Nome ou identificação do colaborador")
        String nomeColaborador,

        @Schema(description = "Cargo do colaborador")
        String cargo,

        @Schema(description = "Loja principal do colaborador")
        String loja,

        @Schema(description = "Quantidade de vendas liquidadas")
        long quantidadeVendas,

        @Schema(description = "Valor total bruto de vendas (R$)")
        BigDecimal valorTotalVendas,

        @Schema(description = "Valor total de comissões apuradas (R$)")
        BigDecimal valorTotalComissao
) {}
