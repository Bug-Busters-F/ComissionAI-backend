package com.bugbusters.backend.dto.interpretador.proposta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Operação de cálculo aplicada sobre a taxa base contratual ou definição direta de percentual")
public enum TipoOperacaoBase {
    @Schema(description = "Substitui a taxa base contratual por um valor percentual fixo direto")
    DEFINIR_TAXA,

    @Schema(description = "Adiciona pontos percentuais à taxa base contratual (ex: base 3% + 1.5% = 4.5%)")
    ACRESCIMO_PONTOS,

    @Schema(description = "Subtrai pontos percentuais da taxa base contratual (ex: base 3% - 0.5% = 2.5%)")
    DESCONTO_PONTOS,

    @Schema(description = "Multiplica a taxa base contratual por um fator (ex: base 3% * 1.5 = 4.5%)")
    MULTIPLICADOR_BASE,

    @Schema(description = "Divide a taxa base contratual por um fator (ex: base 3% / 2 = 1.5%)")
    DIVISOR_BASE
}
