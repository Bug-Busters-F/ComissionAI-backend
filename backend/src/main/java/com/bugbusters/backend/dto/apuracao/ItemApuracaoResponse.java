package com.bugbusters.backend.dto.apuracao;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Detalhamento de uma venda apurada definitivamente pela campanha")
public record ItemApuracaoResponse(
        @Schema(description = "ID do registro de resultado em banco")
        Long id,

        @Schema(description = "Protocolo único da apuração individual")
        UUID protocoloCalculo,

        @Schema(description = "ID da venda transacionada")
        UUID idVenda,

        @Schema(description = "Matrícula do vendedor")
        String matricula,

        @Schema(description = "Código da marca")
        Integer codMarca,

        @Schema(description = "Código da loja")
        Integer codLoja,

        @Schema(description = "Código do cargo")
        Integer codCargo,

        @Schema(description = "Data da venda")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dataVenda,

        @Schema(description = "Valor bruto da venda")
        BigDecimal valorVenda,

        @Schema(description = "Taxa de comissão aplicada (decimal)")
        BigDecimal taxaAplicada,

        @Schema(description = "Valor consolidado da comissão")
        BigDecimal valorComissao,

        @Schema(description = "Valor do bônus percentual aplicado")
        BigDecimal valorBonusPercentual,

        @Schema(description = "Valor do bônus fixo aplicado")
        BigDecimal valorBonusFixo,

        @Schema(description = "ID da regra promocional aplicada")
        Long regraId,

        @Schema(description = "Origem da taxa (REGRA_CAMPANHA ou BASE_COMISS)")
        String origemTaxa,

        @Schema(description = "Data e hora de gravação do cálculo")
        OffsetDateTime calculadoEm
) {}

