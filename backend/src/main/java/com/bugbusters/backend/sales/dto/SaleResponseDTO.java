package com.bugbusters.backend.sales.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.store.Store;

@Schema(description = "Representação da venda individual registrada no sistema")
public record SaleResponseDTO(

        @Schema(description = "Identificador interno gerado pelo sistema", example = "1")
        UUID id,

        @Schema(description = "Matricula relacionada")
        Registration registration,

        @Schema(description = "Marca associada à venda", example = "MARCA_A")
        Brand brand,

         @Schema(description = "Loja onde a venda foi realizada", example = "LOJA_SP_01")
        Store store,

        @Schema(description = "Data da venda ou competência de referência", example = "2026-09-13")
        LocalDate saleDate,

        @Schema(description = "Valor bruto da venda", example = "1500.00")
        BigDecimal value,

        @Schema(description = "Canal de venda", example = "ECOMMERCE")
        String saleChannel,

        @Schema(description = "Momento em que o registro foi criado no sistema")
        OffsetDateTime createdAt

) {}
