package com.bugbusters.backend.dto.interpretador.proposta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Critérios de público-alvo e filtros relacionais da regra")
public record FiltrosRegraDTO(
        @Schema(description = "Canal de venda da aplicação da regra (ex: ECOMMERCE, LOJA_FISICA, BALCAO, PADRAO)", example = "ECOMMERCE")
        String canal,

        @Schema(description = "Código numérico da marca", example = "10")
        Integer codMarca,

        @Schema(description = "Descrição textual da marca", example = "PRETO")
        String descrMarca,

        @Schema(description = "Código numérico da filial / loja", example = "75")
        Integer codLoja,

        @Schema(description = "Código numérico do cargo", example = "100")
        Integer codCargo,

        @Schema(description = "Descrição textual do cargo", example = "VENDEDOR LOJA")
        String descriCargo,

        @Schema(description = "Matrícula individual do vendedor (para regras personalizadas)", example = "MATRIC-56")
        String matricula
) {
    public static FiltrosRegraDTO vazio() {
        return new FiltrosRegraDTO(null, null, null, null, null, null, null);
    }
}
