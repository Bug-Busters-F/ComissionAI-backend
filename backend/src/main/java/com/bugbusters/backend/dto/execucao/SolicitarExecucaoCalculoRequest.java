package com.bugbusters.backend.dto.execucao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SolicitarExecucaoCalculoRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Competência deve estar no formato YYYY-MM") String competencia,
        Boolean recalcular,
        String chaveIdempotencia
) {}
