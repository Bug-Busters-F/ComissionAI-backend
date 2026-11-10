package com.bugbusters.backend.dto.preparacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoricoEdicaoLinhaResponseDTO(
        UUID id,
        int numeroLinha,
        long versaoAnterior,
        long versaoNova,
        String origem,
        String camposAlterados,
        String valoresAnterioresJson,
        String valoresNovosJson,
        String errosAntesJson,
        String errosDepoisJson,
        String motivo,
        String editadoPor,
        OffsetDateTime editadoEm
) {}
