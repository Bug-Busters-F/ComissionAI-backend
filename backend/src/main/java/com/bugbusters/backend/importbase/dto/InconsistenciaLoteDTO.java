package com.bugbusters.backend.importbase.dto;

import com.bugbusters.backend.model.InconsistenciaImportacao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Inconsistência ou validação persistida vinculada a um lote de importação (incluindo falhas e lotes excluídos).
 */
@Schema(description = "Registro persistido de validação ou inconsistência do lote de importação")
public record InconsistenciaLoteDTO(
        Long id,
        Long reportId,
        String idLote,
        int linha,
        String campo,
        String motivo,
        String severidade,
        OffsetDateTime criadoEm
) {
    public static InconsistenciaLoteDTO de(InconsistenciaImportacao inc) {
        return new InconsistenciaLoteDTO(
                inc.getId(),
                inc.getEnvio() != null ? inc.getEnvio().getReportId() : null,
                inc.getEnvio() != null ? inc.getEnvio().getIdLote() : null,
                inc.getLinha(),
                inc.getCampo(),
                inc.getMotivo(),
                inc.getSeveridade(),
                inc.getCriadoEm()
        );
    }
}
