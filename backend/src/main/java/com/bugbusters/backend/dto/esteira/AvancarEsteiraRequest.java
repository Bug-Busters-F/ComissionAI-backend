package com.bugbusters.backend.dto.esteira;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Requisição para avançar etapa na esteira de aprovação")
public record AvancarEsteiraRequest(
        @Schema(description = "Nova etapa alvo da esteira (1 a 4)", example = "2")
        @NotNull(message = "A etapa alvo é obrigatória.")
        @Min(value = 1, message = "A etapa mínima é 1.")
        @Max(value = 4, message = "A etapa máxima é 4.")
        Integer etapaAlvo,

        @Schema(description = "Identificação do usuário que está avançando a esteira", example = "analista@empresa.com")
        String responsavel
) {}

