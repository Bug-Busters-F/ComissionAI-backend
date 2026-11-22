package com.bugbusters.backend.dto.preparacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LotePreparacaoResponseDTO(
        UUID id,
        Long campanhaId,
        String tipoBase,
        String competencia,
        String nomeArquivo,
        String hashConteudo,
        UUID arquivoImportacaoId,
        Long envioArquivoOrigemReportId,
        Long envioArquivoEfetivadoReportId,
        String status,
        String origemCriacao,
        int versaoPreparo,
        boolean aptaParaEfetivacao,
        ResumoContagensPreparacaoDTO contagens,
        OffsetDateTime criadoEm,
        String criadoPor,
        OffsetDateTime revalidadoEm,
        OffsetDateTime efetivadoEm,
        String efetivadoPor,
        OffsetDateTime descartadoEm,
        String descartadoPor,
        String motivoDescarte
) {}
