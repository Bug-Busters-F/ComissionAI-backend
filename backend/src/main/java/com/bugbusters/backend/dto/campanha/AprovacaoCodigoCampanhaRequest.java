package com.bugbusters.backend.dto.campanha;

import jakarta.validation.constraints.NotNull;

public record AprovacaoCodigoCampanhaRequest(
        @NotNull(message = "O campo 'aprovado' é obrigatório.")
        Boolean aprovado,
        String motivo,
        String aprovadoPor,
        String hashArtefatosCodigo
) {
    public AprovacaoCodigoCampanhaRequest(Boolean aprovado, String motivo, String aprovadoPor) {
        this(aprovado, motivo, aprovadoPor, null);
    }
}
