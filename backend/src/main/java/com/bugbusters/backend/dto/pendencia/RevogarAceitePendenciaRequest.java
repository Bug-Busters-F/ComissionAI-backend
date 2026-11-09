package com.bugbusters.backend.dto.pendencia;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Requisição para revogar formalmente o aceite de uma pendência.
 */
@Schema(description = "Dados para revogação formal de aceite de pendência")
public record RevogarAceitePendenciaRequest(

        @Schema(description = "Identificação do responsável pela revogação", example = "compliance.comissoes@empresa.com")
        @NotBlank(message = "A identificação do responsável pela revogação é obrigatória.")
        String revogadoPor,

        @Schema(description = "Justificativa formal da revogação", example = "Revisão de risco reprovada pelo comitê de compliance.")
        @NotBlank(message = "A justificativa formal da revogação é obrigatória.")
        String motivo
) {
}

