package com.bugbusters.backend.importbase.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Resultado da exclusão de um lote de importação")
public record ExclusaoLoteResponseDTO(
    @Schema(description = "Identificador público do relatório/lote", example = "123")
    long reportId,

    @Schema(description = "Status após a exclusão", example = "EXCLUIDO")
    String status,

    @Schema(description = "Quantidade de registros excluídos", example = "50")
    long registrosRemovidos,

    @Schema(description = "Data e hora da exclusão")
    OffsetDateTime excluidoEm,

    @Schema(description = "Usuário responsável pela exclusão")
    String excluidoPor,

    @Schema(description = "Motivo registrado na exclusão", nullable = true)
    String motivo,

    @Schema(description = "Protocolo de auditoria gerado pela exclusão", nullable = true)
    UUID protocoloAuditoria
) {
    /**
     * Construtor de compatibilidade com os 4 parâmetros anteriores.
     */
    public ExclusaoLoteResponseDTO(long reportId, String status, long registrosRemovidos, OffsetDateTime excluidoEm) {
        this(reportId, status, registrosRemovidos, excluidoEm, null, null, null);
    }
}
