package com.bugbusters.backend.dto.relatorio;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Detalhamento linha a linha de venda com comissão e bônus no fechamento")
public record ItemRelatorioFechamentoDTO(
        @Schema(description = "ID da venda transacionada")
        UUID idVenda,

        @Schema(description = "Protocolo de cálculo")
        UUID protocoloCalculo,

        @Schema(description = "Data da venda")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dataVenda,

        @Schema(description = "Matrícula do vendedor")
        String matricula,

        @Schema(description = "Código da loja")
        Integer codLoja,

        @Schema(description = "Nome da loja")
        String nomeLoja,

        @Schema(description = "Código da marca")
        Integer codMarca,

        @Schema(description = "Nome da marca")
        String nomeMarca,

        @Schema(description = "Código do cargo")
        Integer codCargo,

        @Schema(description = "Descrição do cargo")
        String nomeCargo,

        @Schema(description = "Canal de venda")
        String canal,

        @Schema(description = "Valor bruto da venda (R$)")
        BigDecimal valorVenda,

        @Schema(description = "Taxa de comissão aplicada (decimal)")
        BigDecimal taxaAplicada,

        @Schema(description = "Valor da parcela base de comissão (R$)")
        BigDecimal valorComissao,

        @Schema(description = "Valor de bônus percentual (R$)")
        BigDecimal valorBonusPercentual,

        @Schema(description = "Valor de bônus fixo (R$)")
        BigDecimal valorBonusFixo,

        @Schema(description = "Valor total da comissão com bônus (R$)")
        BigDecimal valorTotalComissao,

        @Schema(description = "ID da regra aplicada")
        Long regraId,

        @Schema(description = "Origem da taxa aplicada")
        String origemTaxa,

        @Schema(description = "Número da versão da campanha")
        Integer versaoCampanhaNumero
) {}

