package com.bugbusters.backend.dto.simulacao;

import com.bugbusters.backend.model.StatusEnquadramentoOrcamento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Regras e indicadores de enquadramento orçamentário por cenário simulado (BACK-8)")
public record EnquadramentoOrcamentoCenarioDTO(
        @Schema(description = "Identificador do cenário", example = "100%")
        String cenario,

        @Schema(description = "Fator multiplicador do volume", example = "1.00")
        BigDecimal multiplicadorVolume,

        @Schema(description = "Total de vendas no escopo avaliado (mesmo escopo nos 4 cenários)", example = "350")
        int totalVendasEscopo,

        @Schema(description = "Teto orçamentário da campanha considerado", example = "50000.00")
        BigDecimal orcamentoTeto,

        @Schema(description = "Custo total projetado de comissões no período da campanha (comissaoProjetada)", example = "42500.00")
        BigDecimal custoTotalProjetado,

        @Schema(description = "Delta projetado em relação à base contratual", example = "7500.00")
        BigDecimal deltaProjetado,

        @Schema(description = "Status do enquadramento frente ao teto orçamentário", example = "ENQUADRADO")
        StatusEnquadramentoOrcamento statusEnquadramento,

        @Schema(description = "Indica se o custo projetado cabe dentro do orçamento (custo <= teto)", example = "true")
        Boolean dentroDoOrcamento,

        @Schema(description = "Saldo orçamentário restante (positivo = folga, negativo = déficit)", example = "7500.00")
        BigDecimal saldoOrcamento,

        @Schema(description = "Valor excedente acima do teto orçamentário (0.00 se enquadrado)", example = "0.00")
        BigDecimal excedenteOrcamento,

        @Schema(description = "Percentual de consumo do orçamento pelo custo projetado (custo / teto * 100)", example = "85.00")
        BigDecimal percentualUsoOrcamento
) {}
