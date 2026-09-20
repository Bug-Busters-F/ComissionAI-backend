package com.bugbusters.backend.sales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Payload para registro de uma venda individual")
public record SaleRequestDTO(
        @Schema(description = "Identificador único da venda (UUID opcional para proteção contra duplicidade)", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID id,

        @Schema(description = "Matrícula do funcionário responsável pela venda", example = "MAT-00456")
        @NotNull(message = "A matrícula do funcionário é obrigatória")
        String registrationCode,

        @Schema(description = "Num da Marca associada à venda", example = "10")
        @NotNull(message = "A marca é obrigatória")
        Integer brandCode,

        @Schema(description = "Num da Loja onde a venda foi realizada", example = "62")
        @NotNull(message = "A loja é obrigatória")
        Integer storeCode,

        @Schema(
                description = "Valor bruto da venda. Obrigatoriamente BigDecimal — valores com Double/Float são recusados.",
                example = "1500.00"
        )
        @NotNull(message = "O valor da venda é obrigatório")
        @Positive(message = "O valor da venda deve ser maior que zero")
        BigDecimal value,

        @Schema(
                description = "Data em que a venda foi realizada ou competência de referência (formato: YYYY-MM-DD)",
                example = "2026-09-13"
        )
        @NotNull(message = "A data da venda é obrigatória")
        LocalDate saleDate,

        @Schema(description = "Canal de venda (ex: ECOMMERCE, LOJA_FISICA)", example = "ECOMMERCE")
        @NotBlank(message = "O canal de venda é obrigatório")
        String saleChannel
) {
    public SaleRequestDTO(String registrationCode, Integer brandCode, Integer storeCode, BigDecimal value, LocalDate saleDate, String saleChannel) {
        this(null, registrationCode, brandCode, storeCode, value, saleDate, saleChannel);
    }
}
