package com.bugbusters.backend.dto.esteira;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Requisição formal de devolução de proposta na esteira pelo gerente (FRONT-9 / BACK-4)")
public record DevolverCampanhaRequest(
        @Schema(description = "Etapa de destino para devolução (1: Proposta, 2: Revisão Técnica, 3: Simulação)", example = "1")
        @NotNull(message = "A etapa de destino da devolução é obrigatória.")
        @Min(value = 1, message = "A etapa mínima de destino é 1.")
        @Max(value = 3, message = "A etapa máxima para devolução é 3.")
        Integer etapaDestino,

        @Schema(description = "Justificativa clara e detalhada apontando o que deve ser corrigido", example = "O teto da regra 2 está incorreto, favor ajustar antes de prosseguir.")
        @NotBlank(message = "A justificativa formal de devolução é obrigatória.")
        String motivo,

        @Schema(description = "Identificação do gerente ou revisor que está devolvendo", example = "gerente.vendas@empresa.com")
        @NotBlank(message = "O identificador do responsável pela devolução é obrigatório.")
        String devolvidoPor
) {}

