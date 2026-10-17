package com.bugbusters.backend.dto.interpretador.proposta;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Entrada em linguagem natural e contexto para extração de múltiplas propostas de regras")
public record InterpretacaoMultiplaRequest(
        @Schema(description = "Comando em texto livre com uma ou múltiplas regras descritas pelo gestor", example = "Pagar 5% no ecommerce e para vendas da loja 75 acima de R$ 5.000 pagar taxa base + 1.5%")
        @NotBlank(message = "O texto do comando é obrigatório")
        String texto,

        @Schema(description = "Contexto adicional do negócio (ex: ano_referencia, competência, canal padrão, filtros pré-fixados)", example = "{\"ano_referencia\": 2026, \"mes_referencia\": 10}")
        Map<String, Object> contexto
) {
    public InterpretacaoMultiplaRequest {
        if (contexto == null) {
            contexto = Map.of();
        }
    }
}
