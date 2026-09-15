package com.bugbusters.backend.dto.calculo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Log imutável de auditoria de cálculo financeiro")
public record LogCalculoResponse(
    @Schema(description = "Identificador único do log", example = "7b2e652a-9941-4770-9852-51322ab5e1f0")
    UUID idLog,

    @Schema(description = "Matrícula do colaborador", example = "MATRIC-1")
    String matricula,

    @Schema(description = "ID da regra aplicada", example = "1")
    Long idRegra,

    @Schema(description = "Valor original transacionado", example = "1000.00")
    BigDecimal valorOriginal,

    @Schema(description = "Taxa aplicada", example = "0.1000")
    BigDecimal taxaAplicada,

    @Schema(description = "Valor da comissão calculada", example = "100.00")
    BigDecimal valorComissao,

    @Schema(description = "Data e hora de execução", example = "2026-09-14T09:30:00Z")
    OffsetDateTime executadoEm
) {}

