package com.bugbusters.backend.dto.venda;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Payload para registro de uma venda individual")
public record VendaRequestDTO(

        @Schema(
                description = "Identificador externo da venda, fornecido pelo cliente para garantir idempotência. Deve ser único por venda.",
                example = "VENDA-2026-00123"
        )
        @NotBlank(message = "O identificador externo da venda é obrigatório")
        String idVendaExterno,

        @Schema(description = "Matrícula do funcionário responsável pela venda", example = "MAT-00456")
        @NotBlank(message = "A matrícula do funcionário é obrigatória")
        String matricula,

        @Schema(description = "Canal de venda (ex: ECOMMERCE, LOJA_FISICA)", example = "ECOMMERCE")
        @NotBlank(message = "O canal de venda é obrigatório")
        String canal,

        @Schema(description = "Marca associada à venda", example = "MARCA_A")
        @NotBlank(message = "A marca é obrigatória")
        String marca,

        @Schema(description = "Loja onde a venda foi realizada", example = "LOJA_SP_01")
        @NotBlank(message = "A loja é obrigatória")
        String loja,

        @Schema(
                description = "Data em que a venda foi realizada ou competência de referência (formato: YYYY-MM-DD)",
                example = "2026-09-13"
        )
        @NotNull(message = "A data da venda é obrigatória")
        LocalDate dataVenda,

        @Schema(
                description = "Valor bruto da venda. Obrigatoriamente BigDecimal — valores com Double/Float são recusados.",
                example = "1500.00"
        )
        @NotNull(message = "O valor da venda é obrigatório")
        @Positive(message = "O valor da venda deve ser maior que zero")
        BigDecimal valorVenda

) {}
