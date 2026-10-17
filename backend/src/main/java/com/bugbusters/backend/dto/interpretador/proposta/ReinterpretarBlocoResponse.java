package com.bugbusters.backend.dto.interpretador.proposta;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado da reinterpretação localizada de bloco com lista consolidada de propostas")
public record ReinterpretarBlocoResponse(
        @Schema(description = "Identificador do bloco que foi modificado", example = "bloco-2")
        String blocoModificadoId,

        @Schema(description = "Resumo textual das alterações aplicadas no bloco", example = "Valor mínimo atualizado para R$ 10.000,00 e taxa ajustada para taxa base + 2.00%")
        String resumoModificacao,

        @Schema(description = "Lista completa e consolidada das propostas com o bloco alvo atualizado")
        List<PropostaRegraDTO> propostasAtualizadas
) {
    public ReinterpretarBlocoResponse {
        if (propostasAtualizadas == null) {
            propostasAtualizadas = List.of();
        }
    }
}
