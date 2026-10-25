package com.bugbusters.backend.dto.fechamento;

import com.bugbusters.backend.model.StatusFechamento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Status de bloqueio e situação de fechamento de uma competência (BACK-11)")
public record FechamentoStatusDTO(
        @Schema(description = "Competência consultada", example = "2026-10")
        String competencia,

        @Schema(description = "Status do fechamento da competência (ABERTA se nenhum fechamento concluído)")
        String status,

        @Schema(description = "Indica se a competência está lacrada contra alterações e publicações de regras", example = "true")
        boolean bloqueada,

        @Schema(description = "Identificador do fechamento (se houver)")
        UUID fechamentoId,

        @Schema(description = "Protocolo de auditoria do fechamento")
        UUID protocolo,

        @Schema(description = "Data e hora em que o fechamento foi concluído")
        OffsetDateTime fechadoEm,

        @Schema(description = "Responsável que efetuou o fechamento")
        String executadoPor,

        @Schema(description = "Mensagem explicativa sobre a situação da competência")
        String mensagem
) {}
