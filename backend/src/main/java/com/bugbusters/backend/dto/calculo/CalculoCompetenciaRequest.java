package com.bugbusters.backend.dto.calculo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Entrada para processamento do cálculo de comissões por competência")
public record CalculoCompetenciaRequest(
    @Schema(description = "Mês/Ano de competência no formato YYYY-MM (ex: 2026-09) ou YYYY-MM-DD (ex: 2026-09-01)", example = "2026-09")
    @NotBlank(message = "A competência é obrigatória")
    @Pattern(regexp = "^\\d{4}-\\d{2}(-\\d{2})?$", message = "Formato de competência inválido. Utilize YYYY-MM (ex: 2026-09) ou YYYY-MM-DD (ex: 2026-09-01)")
    String competencia
) {}
