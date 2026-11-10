package com.bugbusters.backend.dto.programapython;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload para formalização da aprovação técnica do código executável da campanha")
public record AprovarProgramaPythonRequest(
        @NotBlank(message = "A identificação do revisor técnico é obrigatória.")
        @Schema(description = "Identificação do revisor técnico responsável", example = "Renan Tomasi")
        String aprovadoPor,

        @Schema(description = "Checksum SHA-256 esperado do código. Se informado, valida se o código não foi alterado concorrentemente antes do clique", example = "a6c1e5...")
        String checksumEsperado,

        @Schema(description = "Parecer ou justificativa técnica da homologação", example = "Código auditado conforme parâmetros comerciais e regras de compliance.")
        String justificativa
) {
}

