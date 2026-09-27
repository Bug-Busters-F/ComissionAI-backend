package com.bugbusters.backend.dto.calculo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Detalhe do cálculo de comissão de uma venda processada com sucesso")
public record CalculoItemResponseDTO(
    @Schema(description = "UUID imutável gerado para auditoria do cálculo", example = "7b2e652a-9941-4770-9852-51322ab5e1f0")
    UUID protocoloCalculo,

    @Schema(description = "Identificador único da venda (UUID da Sale)", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    UUID idVenda,

    @Schema(description = "Matrícula do colaborador", example = "MATRIC-1")
    String matricula,

    @Schema(description = "Código do cargo do colaborador", example = "100")
    Integer codCargo,

    @Schema(description = "Código da marca da venda", example = "10")
    Integer codMarca,

    @Schema(description = "Código da loja da venda", example = "75")
    Integer codLoja,

    @Schema(description = "Data de ocorrência da venda", example = "2026-09-15")
    LocalDate dataVenda,

    @Schema(description = "Valor bruto da venda (R$)", example = "1000.00")
    BigDecimal valorVenda,

    @Schema(description = "Taxa decimal utilizada no cálculo (escala 4)", example = "0.1000")
    BigDecimal taxaAplicada,

    @Schema(description = "Valor apurado da comissão a pagar (R$, escala 2, arredondamento HALF_UP)", example = "100.00")
    BigDecimal valorComissao,

    @Schema(description = "Identificador da regra ou taxa de comissão aplicada", example = "1")
    Long idRegra,

    @Schema(description = "Origem da taxa de comissão aplicada", example = "BASE_COMISS")
    String origemTaxa,

    @Schema(description = "Data e hora do processamento", example = "2026-09-25T14:30:00Z")
    OffsetDateTime dataCalculo
) {}
