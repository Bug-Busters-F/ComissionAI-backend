package com.bugbusters.backend.service.pendencia;

import java.time.OffsetDateTime;

/**
 * Representa o estado de aceite de uma inconsistência/pendência (integração com BACK-7).
 */
public record StatusAceitePendenciaDTO(
        boolean aceita,
        String aceitaPor,
        OffsetDateTime aceitaEm,
        String motivoAceite
) {
    public static StatusAceitePendenciaDTO naoAceita() {
        return new StatusAceitePendenciaDTO(false, null, null, null);
    }

    public static StatusAceitePendenciaDTO aceita(String aceitaPor, OffsetDateTime aceitaEm, String motivo) {
        return new StatusAceitePendenciaDTO(true, aceitaPor, aceitaEm, motivo);
    }
}
