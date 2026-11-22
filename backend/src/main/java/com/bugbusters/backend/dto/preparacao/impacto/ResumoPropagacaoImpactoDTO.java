package com.bugbusters.backend.dto.preparacao.impacto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ResumoPropagacaoImpactoDTO(
        UUID propagacaoId,
        int qtdCampanhasAfetadas,
        int qtdRegrasAfetadas,
        int qtdSimulacoesInvalidadas,
        int qtdCalculosDesatualizados,
        int qtdAprovacoesCodigoInvalidadas,
        List<String> competenciasAfetadas,
        OffsetDateTime propagadoEm,
        String confirmadoPor,
        String mensagem
) {}
