package com.bugbusters.backend.dto.programapython;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload para formalização da aprovação técnica do código executável da campanha")
public record AprovarProgramaPythonRequest(
        @Schema(description = "Identificação do revisor técnico responsável", example = "Renan Tomasi")
        String aprovadoPor,

        @NotBlank(message = "O checksum SHA-256 esperado do código é obrigatório para confirmar a integridade da revisão.")
        @Schema(description = "Checksum SHA-256 esperado do código. Valida se o código não foi alterado concorrentemente antes do clique", example = "a6c1e5...")
        String checksumEsperado,

        @Schema(description = "Parecer ou justificativa técnica da homologação", example = "Código auditado conforme parâmetros comerciais e regras de compliance.")
        String justificativa
) {
}

