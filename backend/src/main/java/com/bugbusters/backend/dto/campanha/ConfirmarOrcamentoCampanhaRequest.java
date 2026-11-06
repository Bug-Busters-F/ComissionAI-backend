package com.bugbusters.backend.dto.campanha;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "Requisição para confirmação ou alteração explícita do orçamento da campanha antes de simular")
public record ConfirmarOrcamentoCampanhaRequest(
        @Schema(description = "Valor monetário do orçamento confirmado ou alterado", example = "50000.00")
        @NotNull(message = "O valor do orçamento é obrigatório")
        @DecimalMin(value = "0.01", message = "O orçamento deve ser maior que zero")
        BigDecimal orcamento,

        @Schema(description = "Valor original extraído pela IA (opcional, para auditoria de alteração)", example = "45000.00")
        BigDecimal orcamentoExtraidoIa,

        @Schema(description = "Responsável pela confirmação/alteração", example = "analista.comissoes@empresa.com")
        String responsavel,

        @Schema(description = "Justificativa opcional (recomendada se o valor for alterado em relação ao extraído)", example = "Ajuste conforme deliberação da diretoria comercial")
        String justificativa,

        @Schema(description = "Versão esperada da campanha para controle de concorrência otimista", example = "1")
        Integer versaoEsperada
) {}
