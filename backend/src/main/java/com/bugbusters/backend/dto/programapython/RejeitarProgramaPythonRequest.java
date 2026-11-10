package com.bugbusters.backend.dto.programapython;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload para rejeição ou devolução do código executável da campanha")
public record RejeitarProgramaPythonRequest(
        @NotBlank(message = "A identificação do revisor técnico é obrigatória.")
        @Schema(description = "Identificação do revisor técnico que rejeitou o código", example = "Renan Tomasi")
        String rejeitadoPor,

        @NotBlank(message = "A justificativa ou motivo da recusa é obrigatória para orientar a correção.")
        @Schema(description = "Motivo técnico detalhado da rejeição", example = "A regra de faixa mínima está rejeitando vendas de valor igual a 1000.")
        String justificativa
) {
}

