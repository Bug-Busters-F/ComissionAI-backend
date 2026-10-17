package com.bugbusters.backend.dto.interpretador.proposta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Registro de referência consultada nos cadastros do sistema durante a interpretação")
public record ReferenciaConsultadaDTO(
        @Schema(description = "Tipo de dimensão consultada (ex: MARCA, CARGO, LOJA, CANAL, BASE_COMISS, REGRA_CONCORRENTE)", example = "BASE_COMISS")
        String tipo,

        @Schema(description = "Chave ou identificador da referência", example = "cod_marca=10,cod_cargo=100")
        String identificador,

        @Schema(description = "Descrição contextual da referência encontrada", example = "Base Contratual PRETO / VENDEDOR LOJA (taxa 3.00%)")
        String descricao
) {}
