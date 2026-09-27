package com.bugbusters.backend.dto.calculo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Registro de venda com impedimento para o cálculo de comissão")
public record CalculoImpedimentoDTO(
    @Schema(description = "Identificador único da venda (UUID)", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    UUID idVenda,

    @Schema(description = "Matrícula do colaborador associada à venda", example = "MATRIC-999")
    String matricula,

    @Schema(description = "Data de ocorrência da venda", example = "2026-09-15")
    LocalDate dataVenda,

    @Schema(description = "Valor bruto da venda", example = "1500.00")
    BigDecimal valorVenda,

    @Schema(description = "Motivo detalhado do impedimento", example = "Taxa base de comissão não encontrada para Marca 10 e Cargo 200")
    String motivo
) {}
