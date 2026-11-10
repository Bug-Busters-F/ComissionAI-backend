package com.bugbusters.backend.dto.preparacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoricoVersaoRegistroResponseDTO(
        UUID id,
        String tipoBase,
        UUID registroId,
        String chaveNegocio,
        int versao,
        String tipoOperacao,
        String origem,
        Long campanhaId,
        UUID lotePreparacaoId,
        String idLote,
        String valoresAnterioresJson,
        String valoresNovosJson,
        String registradoPor,
        OffsetDateTime registradoEm
) {}
