package com.bugbusters.backend.dto.campanha;

public record AprovacaoCodigoCampanhaRequest(
        Boolean aprovado,
        String motivo,
        String aprovadoPor
) {}
