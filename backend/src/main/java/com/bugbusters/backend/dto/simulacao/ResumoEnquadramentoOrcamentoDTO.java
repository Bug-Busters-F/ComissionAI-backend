package com.bugbusters.backend.dto.simulacao;

import com.bugbusters.backend.model.OrigemOrcamentoCampanha;
import com.bugbusters.backend.model.StatusEnquadramentoOrcamento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "Consolidado de governança e enquadramento orçamentário da simulação nos 4 cenários (BACK-8)")
public record ResumoEnquadramentoOrcamentoDTO(
        @Schema(description = "Teto orçamentário monetário da campanha", example = "50000.00")
        BigDecimal orcamentoCampanha,

        @Schema(description = "Indica se o orçamento da campanha foi confirmado explicitamente", example = "true")
        Boolean orcamentoConfirmado,

        @Schema(description = "Origem do orçamento considerado", example = "CONFIRMADO_USUARIO")
        OrigemOrcamentoCampanha origemOrcamento,

        @Schema(description = "Total de vendas do escopo comum avaliado nos 4 cenários", example = "350")
        int totalVendasEscopoComum,

        @Schema(description = "Garante que o mesmo escopo foi avaliado rigorosamente nos 4 cenários", example = "true")
        boolean escopoConsistenteQuatroCenarios,

        @Schema(description = "Status de enquadramento do cenário 100%", example = "ENQUADRADO")
        StatusEnquadramentoOrcamento statusCenario100,

        @Schema(description = "Indica se a simulação está apta para encaminhamento ao gerente (100% enquadrado e sem impedimentos)", example = "true")
        boolean aptoEncaminhamentoGerente,

        @Schema(description = "Motivo de bloqueio para o encaminhamento ao gerente (se houver)", example = "Custo projetado em 100% excede o teto orçamentário.")
        String motivoBloqueioEncaminhamento,

        @Schema(description = "Detalhamento do enquadramento em cada cenário simulado (80%, 100%, 120%, 150%)")
        Map<String, EnquadramentoOrcamentoCenarioDTO> cenarios
) {}
