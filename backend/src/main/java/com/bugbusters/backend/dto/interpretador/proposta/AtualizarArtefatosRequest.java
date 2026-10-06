package com.bugbusters.backend.dto.interpretador.proposta;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Requisição para sincronização e atualização imediata dos artefatos explicativos após edição manual de campos")
public record AtualizarArtefatosRequest(
        @Schema(description = "Proposta de regra após as alterações manuais do usuário no formulário do frontend")
        @NotNull(message = "A proposta a ser atualizada é obrigatória")
        @Valid
        PropostaRegraDTO proposta
) {}
