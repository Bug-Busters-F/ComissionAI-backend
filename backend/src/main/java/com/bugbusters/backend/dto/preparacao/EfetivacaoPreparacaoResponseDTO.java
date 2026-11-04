package com.bugbusters.backend.dto.preparacao;

import com.bugbusters.backend.dto.preparacao.impacto.ResumoPropagacaoImpactoDTO;
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
        String efetivadoPor,
        ResumoPropagacaoImpactoDTO resumoImpactos
) {
    public EfetivacaoPreparacaoResponseDTO(
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
    ) {
        this(preparacaoId, campanhaId, status, reportIdGerado, idLoteGerado, anexoCampanhaId,
                registrosInseridos, registrosAtualizados, registrosIgnoradosDuplicados,
                advertenciasRegistradas, efetivadoEm, efetivadoPor, null);
    }
}
