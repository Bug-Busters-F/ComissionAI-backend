package com.bugbusters.backend.dto.interpretador.proposta;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados da taxa base contratual consultada no banco de dados para ajustes de comissão")
public record ReferenciaBaseDTO(
        @Schema(description = "Tipo de base de comissão consultada", example = "BASE_COMISS")
        String tipoBase,

        @Schema(description = "Valor da taxa base percentual localizada (escala 4)", example = "0.0300")
        BigDecimal taxaBaseConsultada,

        @Schema(description = "Descrição contextual do vínculo da base consultada", example = "Base Contratual Marca PRETO / VENDEDOR LOJA")
        String descricao
) {}
