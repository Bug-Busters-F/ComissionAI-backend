package com.bugbusters.backend.dto.relatorio;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Resumo executivo consolidado do fechamento de comissões")
public record ResumoFechamentoDTO(
        @Schema(description = "Identificador do fechamento ou campanha", example = "Competência 2026-10")
        String identificador,

        @Schema(description = "Protocolo oficial de liquidação")
        UUID protocolo,

        @Schema(description = "Status do fechamento", example = "CONCLUIDO")
        String status,

        @Schema(description = "Competência no formato YYYY-MM", example = "2026-10")
        String competencia,

        @Schema(description = "Data inicial considerada no período")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dataInicio,

        @Schema(description = "Data final considerada no período")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dataFim,

        @Schema(description = "Responsável pela execução", example = "controladoria@empresa.com")
        String executadoPor,

        @Schema(description = "Momento de conclusão da apuração")
        OffsetDateTime executadoEm,

        @Schema(description = "Quantidade de vendas processadas")
        int totalVendasProcessadas,

        @Schema(description = "Quantidade de vendas calculadas com sucesso")
        int totalCalculados,

        @Schema(description = "Quantidade de impedimentos detectados")
        int totalImpedimentos,

        @Schema(description = "Valor total bruto das vendas (R$)")
        BigDecimal valorTotalVendas,

        @Schema(description = "Valor total de comissões apuradas (R$)")
        BigDecimal valorTotalComissao,

        @Schema(description = "Valor total de bônus percentual apurado (R$)")
        BigDecimal valorTotalBonusPercentual,

        @Schema(description = "Valor total de bônus fixo apurado (R$)")
        BigDecimal valorTotalBonusFixo,

        @Schema(description = "Valor total de bônus somados (R$)")
        BigDecimal valorTotalBonus,

        @Schema(description = "Valor total líquido a pagar aos colaboradores (R$)")
        BigDecimal valorTotalLiquidoAPagar,

        @Schema(description = "Taxa média efetiva praticada no fechamento (percentual)")
        BigDecimal taxaMediaEfetiva,

        @Schema(description = "Quantidade de colaboradores/vendedores distintos contemplados")
        int totalColaboradoresDistintos,

        @Schema(description = "Quantidade de lojas distintas contempladas")
        int totalLojasDistintas,

        @Schema(description = "Quantidade de canais de venda distintos contemplados")
        int totalCanaisDistintos
) {}

