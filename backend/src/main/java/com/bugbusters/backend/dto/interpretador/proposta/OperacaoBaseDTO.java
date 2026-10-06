package com.bugbusters.backend.dto.interpretador.proposta;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Operação de ajuste aplicada sobre a base contratual ou definição de percentual")
public record OperacaoBaseDTO(
        @Schema(description = "Tipo da operação de cálculo", example = "ACRESCIMO_PONTOS")
        @NotNull(message = "O tipo da operação base é obrigatório")
        TipoOperacaoBase tipoOperacao,

        @Schema(description = "Valor do ajuste ou parâmetro da operação (escala 4, ex: 0.0150 para +1.5% ou fator 1.5)", example = "0.0150")
        BigDecimal valorAjuste,

        @Schema(description = "Taxa base de referência contratual consultada no banco de dados")
        ReferenciaBaseDTO referenciaBase
) {
    public static OperacaoBaseDTO definirTaxa(BigDecimal taxa) {
        return new OperacaoBaseDTO(TipoOperacaoBase.DEFINIR_TAXA, taxa, null);
    }
}
