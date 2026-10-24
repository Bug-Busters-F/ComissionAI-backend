package com.bugbusters.backend.dto.campanha;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VersaoCampanhaResumoResponse(
        Integer numero,
        Integer versaoAnterior,
        String responsavel,
        OffsetDateTime publicadoEm,
        UUID simulacaoAceitaId,
        UUID protocoloSimulacao
) {}
