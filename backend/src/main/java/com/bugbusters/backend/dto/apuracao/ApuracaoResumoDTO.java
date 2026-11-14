package com.bugbusters.backend.dto.apuracao;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Resumo executivo de uma apuração histórica de campanha")
public record ApuracaoResumoDTO(
        @Schema(description = "Identificador único da apuração")
        UUID apuracaoId,

        @Schema(description = "Protocolo de liquidação")
        UUID protocolo,

        @Schema(description = "ID da campanha")
        Long campanhaId,

        @Schema(description = "Número da versão da campanha")
        Integer versaoCampanhaNumero,

        @Schema(description = "Competência no formato YYYY-MM")
        String competencia,

        @Schema(description = "Data inicial")
        LocalDate dataInicio,

        @Schema(description = "Data final")
        LocalDate dataFim,

        @Schema(description = "Versão do programa Python homologado")
        Integer versaoProgramaPython,

        @Schema(description = "Checksum do programa Python")
        String checksumPython,

        @Schema(description = "Executado via motor Python")
        boolean executadoViaPython,

        @Schema(description = "Total de vendas processadas")
        int totalVendasProcessadas,

        @Schema(description = "Total de vendas calculadas com sucesso")
        int totalCalculados,

        @Schema(description = "Total de vendas com impedimentos")
        int totalImpedimentos,

        @Schema(description = "Valor total das vendas")
        BigDecimal valorTotalVendas,

        @Schema(description = "Valor total da comissão apurada")
        BigDecimal valorTotalComissao,

        @Schema(description = "Valor total do bônus apurado")
        BigDecimal valorTotalBonus,

        @Schema(description = "Usuário executor")
        String executadoPor,

        @Schema(description = "Data e hora de execução")
        OffsetDateTime executadoEm,

        @Schema(description = "Status da apuração")
        String status
) {}

