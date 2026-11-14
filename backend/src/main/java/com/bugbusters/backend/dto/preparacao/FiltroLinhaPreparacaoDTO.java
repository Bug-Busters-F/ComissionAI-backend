package com.bugbusters.backend.dto.preparacao;

import java.time.LocalDate;

public record FiltroLinhaPreparacaoDTO(
        Boolean valida,
        String severidade,
        String campoErro,
        String matricula,
        LocalDate dataInicio,
        LocalDate dataFim,
        Boolean editada,
        Boolean ignorada
) {}
