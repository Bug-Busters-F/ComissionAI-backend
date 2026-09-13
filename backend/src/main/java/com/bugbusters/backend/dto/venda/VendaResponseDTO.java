package com.bugbusters.backend.dto.venda;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "Representação da venda individual registrada no sistema")
public record VendaResponseDTO(

        @Schema(description = "Identificador interno gerado pelo sistema", example = "1")
        Long id,

        @Schema(description = "Identificador externo fornecido pelo cliente", example = "VENDA-2026-00123")
        String idVendaExterno,

        @Schema(description = "Matrícula do funcionário", example = "MAT-00456")
        String matricula,

        @Schema(description = "Canal de venda", example = "ECOMMERCE")
        String canal,

        @Schema(description = "Marca associada à venda", example = "MARCA_A")
        String marca,

        @Schema(description = "Loja onde a venda foi realizada", example = "LOJA_SP_01")
        String loja,

        @Schema(description = "Data da venda ou competência de referência", example = "2026-09-13")
        LocalDate dataVenda,

        @Schema(description = "Valor bruto da venda", example = "1500.00")
        BigDecimal valorVenda,

        @Schema(description = "Momento em que o registro foi criado no sistema")
        OffsetDateTime criadoEm

) {}
