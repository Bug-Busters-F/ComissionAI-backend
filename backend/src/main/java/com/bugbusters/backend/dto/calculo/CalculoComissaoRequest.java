package com.bugbusters.backend.dto.calculo;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Entrada para apuração de comissão sobre uma venda")
public record CalculoComissaoRequest(
    @Schema(description = "Matrícula cadastral do colaborador (chave de vínculo com o RH)", example = "MATRIC-1")
    @NotBlank(message = "A matrícula é obrigatória")
    String matricula,

    @Schema(description = "Valor bruto transacionado", example = "1000.00")
    @NotNull(message = "O valor da venda é obrigatório")
    @Positive(message = "O valor da venda deve ser positivo")
    BigDecimal valorVenda,

    @Schema(description = "Data de ocorrência da venda", example = "2025-12-01")
    @NotNull(message = "A data da venda é obrigatória")
    LocalDate dataVenda,

    @Schema(description = "Código da marca da venda", example = "10")
    Integer codMarca,

    @Schema(description = "Código da loja da venda", example = "75")
    Integer codLoja,

    @Schema(description = "Canal onde a venda ocorreu (fallback padrão: PADRAO)", example = "LOJA_FISICA", defaultValue = "PADRAO")
    String canal
) {
    public CalculoComissaoRequest {
        if (canal == null || canal.isBlank()) {
            canal = "PADRAO";
        }
    }
}