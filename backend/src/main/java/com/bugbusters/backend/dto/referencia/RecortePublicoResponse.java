package com.bugbusters.backend.dto.referencia;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Resultado consolidado do recorte de público da campanha em uma competência de referência")
public record RecortePublicoResponse(
        @Schema(description = "Competência de referência consultada", example = "2026-09")
        String competencia,

        @Schema(description = "Total de transações de vendas existentes no mês na base", example = "9965")
        int totalVendasCompetencia,

        @Schema(description = "Volume financeiro total transacionado no mês na base", example = "1543200.50")
        BigDecimal valorTotalCompetencia,

        @Schema(description = "Total de vendas da competência pertencentes ao público da campanha (união sem duplicação)", example = "350")
        int totalVendasRecortadas,

        @Schema(description = "Volume financeiro total das vendas no público recortado", example = "250000.00")
        BigDecimal valorTotalRecortado,

        @Schema(description = "Percentual de vendas do mês abrangidas pelo público da campanha", example = "3.51")
        BigDecimal percentualCoberturaVendas,

        @Schema(description = "Percentual do volume financeiro do mês abrangido pelo público da campanha", example = "16.20")
        BigDecimal percentualCoberturaValor,

        @Schema(description = "Quantidade de vendas recortadas totalmente aptas para cálculo sem impedimento", example = "345")
        int totalVendasAptas,

        @Schema(description = "Quantidade de vendas recortadas com impedimentos cadastrais ou contratuais", example = "5")
        int totalVendasComImpedimento,

        @Schema(description = "Quantidade de vendas recortadas que atingiram a meta de faixa de valor da regra", example = "280")
        int totalVendasAtendemFaixa,

        @Schema(description = "Quantidade de vendas no público recortado que incidem em mais de uma regra", example = "42")
        int totalVendasSobrepostas,

        @Schema(description = "Indica se existe sobreposição com empate na maior especificidade (risco de bloqueio)", example = "false")
        boolean possuiAmbiguidadeCritica,

        @Schema(description = "Impedimentos gerais da referência ou da coleção de regras")
        List<String> impedimentosGerais,

        @Schema(description = "Diagnóstico consolidado de regras com sobreposição na campanha")
        List<SobreposicaoRegraDTO> analiseSobreposicoes,

        @Schema(description = "Total de vendas retornadas nesta página de listagem", example = "200")
        int totalVendasPagina,

        @Schema(description = "Listagem de vendas recortadas pertencentes ao público da campanha")
        List<VendaRecortadaDTO> vendas
) {}
