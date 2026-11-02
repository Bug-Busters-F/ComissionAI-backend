package com.bugbusters.backend.dto.preparacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EfetivacaoPreparacaoResponseDTO(
        UUID preparacaoId,
        Long campanhaId,
        String status,
        Long reportIdGerado,
        String idLoteGerado,
        UUID anexoCampanhaId,
        int registrosInseridos,
        int registrosAtualizados,
        int registrosIgnoradosDuplicados,
        int advertenciasRegistradas,
        OffsetDateTime efetivadoEm,
        String efetivadoPor
) {}
