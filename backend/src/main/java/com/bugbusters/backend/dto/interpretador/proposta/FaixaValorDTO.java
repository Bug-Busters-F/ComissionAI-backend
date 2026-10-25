package com.bugbusters.backend.dto.interpretador.proposta;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Limites monetários de valor de venda com suporte a intervalos inclusivos ou exclusivos")
public record FaixaValorDTO(
        @Schema(description = "Valor mínimo de venda para elegibilidade à regra (escala 2)", example = "5000.00")
        BigDecimal valorMinimo,

        @Schema(description = "Indica se o limite inferior é inclusivo (true: >= valorMinimo; false: > valorMinimo)", example = "true")
        Boolean minInclusivo,

        @Schema(description = "Valor máximo de venda para elegibilidade à regra (escala 2)", example = "15000.00")
        BigDecimal valorMaximo,

        @Schema(description = "Indica se o limite superior é inclusivo (true: <= valorMaximo; false: < valorMaximo)", example = "false")
        Boolean maxInclusivo
) {
    public FaixaValorDTO {
        if (minInclusivo == null && valorMinimo != null) {
            minInclusivo = true;
        }
        if (maxInclusivo == null && valorMaximo != null) {
            maxInclusivo = true;
        }
    }

    public static FaixaValorDTO semLimites() {
        return new FaixaValorDTO(null, null, null, null);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean possuiFaixa() {
        return valorMinimo != null || valorMaximo != null;
    }
}
