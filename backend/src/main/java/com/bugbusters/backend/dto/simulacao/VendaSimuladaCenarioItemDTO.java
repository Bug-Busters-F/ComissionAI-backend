package com.bugbusters.backend.dto.simulacao;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Resultado do cálculo simulado para uma venda específica em um determinado cenário de sensibilidade")
public record VendaSimuladaCenarioItemDTO(
        @Schema(description = "Fator multiplicador do cenário (0.80, 1.00, 1.20, 1.50)", example = "1.20")
        BigDecimal fatorEscala,

        @Schema(description = "Valor da venda ajustado pelo fator de escala", example = "6000.00")
        BigDecimal valorVenda,

        @Schema(description = "Identificador da regra aplicada (ou null se recorreu ao fallback na base)", example = "2")
        Long regraId,

        @Schema(description = "Nome da regra aplicada (ou indicação de base)", example = "Regra E-commerce 5K+")
        String regraNome,

        @Schema(description = "Origem da taxa aplicada", example = "REGRA_PROPOSTA")
        String origemTaxa,

        @Schema(description = "Indica se o valor desta venda atende à faixa da regra aplicada", example = "true")
        boolean atendeFaixaValor,

        @Schema(description = "Taxa percentual final aplicada a esta venda", example = "0.0500")
        BigDecimal taxaAplicada,

        @Schema(description = "Valor da parcela de bônus percentual aplicado à venda", example = "60.00")
        BigDecimal valorBonusPercentual,

        @Schema(description = "Valor do bônus fixo aplicado à venda", example = "50.00")
        BigDecimal valorBonusFixo,

        @Schema(description = "Valor total da comissão calculada (base + bônus percentual + bônus fixo)", example = "410.00")
        BigDecimal valorComissao,

        @Schema(description = "Indica se a regra ou faixa mudou em relação ao cenário 100%", example = "true")
        boolean mudouFaixaEmRelacaoA100
) {}
