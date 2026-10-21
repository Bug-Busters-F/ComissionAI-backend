package com.bugbusters.backend.dto.simulacao;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Indicadores financeiros consolidados de um cenário simulado")
public record CenarioConsolidadoDTO(
        @Schema(description = "Fator multiplicador do volume de vendas (ex: 0.80, 1.00, 1.20, 1.50)", example = "1.00")
        BigDecimal fatorEscala,

        @Schema(description = "Nome descritivo do cenário", example = "Cenário 100% (Proposta)")
        String descricao,

        @Schema(description = "Volume financeiro total de vendas do recorte neste cenário", example = "1250000.00")
        BigDecimal totalVendas,

        @Schema(description = "Total de comissão calculado pela base contratual (tb_basecomiss) para este volume", example = "37500.00")
        BigDecimal totalComissaoBase,

        @Schema(description = "Total de comissão calculado pela proposta revisada (ou fallback na base)", example = "56250.00")
        BigDecimal totalComissaoProposta,

        @Schema(description = "Diferença financeira absoluta (Proposta - Base)", example = "18750.00")
        BigDecimal deltaComissao,

        @Schema(description = "Variação percentual do custo de comissões em relação à base ((Proposta - Base) / Base * 100)", example = "50.00")
        BigDecimal deltaPercentual,

        @Schema(description = "Taxa média efetiva da base contratual (ComissaoBase / Vendas)", example = "0.0300")
        BigDecimal taxaMediaEfetivaBase,

        @Schema(description = "Taxa média efetiva da proposta (ComissaoProposta / Vendas)", example = "0.0450")
        BigDecimal taxaMediaEfetivaProposta,

        @Schema(description = "Comissão projetada proporcional aos dias inclusivos da campanha (TotalComissao * dias / 30)", example = "28125.00")
        BigDecimal comissaoProjetada,

        @Schema(description = "Delta financeiro projetado proporcional aos dias inclusivos da campanha (DeltaComissao * dias / 30)", example = "9375.00")
        BigDecimal deltaProjetado,

        @Schema(description = "Quantidade de vendas que mudaram de faixa/regra neste cenário em relação ao cenário de 100%", example = "42")
        int qtdVendasComMudancaFaixa,

        @Schema(description = "Quantidade de vendas contempladas por regra da proposta", example = "350")
        int qtdVendasComRegraAplicada,

        @Schema(description = "Quantidade de vendas que recorreram ao fallback na base contratual", example = "50")
        int qtdVendasComFallbackBase
) {}
