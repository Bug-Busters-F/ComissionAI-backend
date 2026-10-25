package com.bugbusters.backend.dto.referencia;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Informações cadastrais e de cobertura da base para seleção de uma competência de referência")
public record CompetenciaReferenciaDTO(
        @Schema(description = "Identificador da competência no formato YYYY-MM", example = "2026-09")
        String competencia,

        @Schema(description = "Quantidade total de transações de vendas no mês", example = "9965")
        int totalVendas,

        @Schema(description = "Soma do valor bruto transacionado nas vendas do mês", example = "1543200.50")
        BigDecimal valorTotalVendas,

        @Schema(description = "Data da primeira venda registrada na competência", example = "2026-09-01")
        LocalDate dataInicio,

        @Schema(description = "Data da última venda registrada na competência", example = "2026-09-30")
        LocalDate dataFim,

        @Schema(description = "Quantidade de vendas com vínculos e vigência de RH válidos", example = "9960")
        int totalVendasComVinculoValido,

        @Schema(description = "Quantidade de vendas com inconsistências de cadastro ou vigência", example = "5")
        int totalVendasComImpedimento,

        @Schema(description = "Indica se há taxas base cadastradas em tb_basecomiss para a competência", example = "true")
        boolean possuiBaseComiss,

        @Schema(description = "Quantidade de combinações Marca/Cargo cadastradas em tb_basecomiss", example = "30")
        int totalTaxasBaseCadastradas,

        @Schema(description = "Status de aptidão da competência como referência (DISPONIVEL, PARCIAL, INSUFICIENTE, SEM_VENDAS)", example = "DISPONIVEL")
        String statusReferencia,

        @Schema(description = "Mensagens contextuais e alertas sobre a integridade da referência")
        List<String> mensagens
) {}
