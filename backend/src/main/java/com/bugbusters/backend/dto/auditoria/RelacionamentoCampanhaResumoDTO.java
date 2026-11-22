package com.bugbusters.backend.dto.auditoria;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resumo relacional de campanha vinculada a um evento de auditoria ou cálculo.
 */
@Schema(description = "Relacionamento do evento com campanha e versão")
public record RelacionamentoCampanhaResumoDTO(
        Long campanhaId,
        String titulo,
        String estado,
        Long campanhaVersaoId,
        Integer versaoNumero,
        String versaoStatus,
        String estadoEsteira
) {}
