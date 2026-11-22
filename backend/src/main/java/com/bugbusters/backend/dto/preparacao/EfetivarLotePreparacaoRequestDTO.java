package com.bugbusters.backend.dto.preparacao;

public record EfetivarLotePreparacaoRequestDTO(
        String responsavel,
        String motivo,
        Boolean confirmarPropagacaoImpacto
) {
    public EfetivarLotePreparacaoRequestDTO(String responsavel, String motivo) {
        this(responsavel, motivo, false);
    }
}
