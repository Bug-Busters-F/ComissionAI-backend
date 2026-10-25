package com.bugbusters.backend.dto.simulacao;

import com.bugbusters.backend.model.StatusAceiteSimulacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Confirmação e metadados de auditoria do aceite formal de uma simulação")
public record AceiteSimulacaoResponse(
        @Schema(description = "Identificador único da simulação aceita (UUID)")
        UUID simulacaoId,

        @Schema(description = "Protocolo único da simulação aceita")
        UUID protocolo,

        @Schema(description = "Identificador da campanha vinculada")
        Long campanhaId,

        @Schema(description = "Título da campanha")
        String tituloCampanha,

        @Schema(description = "Status atual do aceite da simulação", example = "ACEITA")
        StatusAceiteSimulacao statusAceite,

        @Schema(description = "Data e hora do registro de aceite")
        OffsetDateTime aceitoEm,

        @Schema(description = "Responsável pelo aceite")
        String aceitoPor,

        @Schema(description = "Justificativa informada no aceite")
        String motivoAceite,

        @Schema(description = "Assinatura canônica criptográfica (SHA-256) da proposta congelada no aceite")
        String hashProposta,

        @Schema(description = "Mensagem descritiva do resultado da operação")
        String mensagem
) {}
