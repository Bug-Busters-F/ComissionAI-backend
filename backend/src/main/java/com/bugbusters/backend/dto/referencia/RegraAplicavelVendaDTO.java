package com.bugbusters.backend.dto.referencia;

import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Identificação de uma regra de negócio que contempla o público-alvo da venda")
public record RegraAplicavelVendaDTO(
        @Schema(description = "ID da regra cadastrada ou proposta", example = "10")
        Long regraId,

        @Schema(description = "Identificador textual do bloco de origem quando aplicável", example = "bloco-1")
        String blocoId,

        @Schema(description = "Nome descritivo da regra", example = "Regra E-commerce 1K a 5K")
        String nomeRegra,

        @Schema(description = "Peso determinístico de especificidade da regra (1 a 31)", example = "8")
        int especificidade,

        @Schema(description = "Indica se o valor da venda atende aos limites de valor definidos na regra", example = "true")
        boolean atendeFaixa,

        @Schema(description = "Taxa nominal definida ou resultante da regra", example = "0.0500")
        BigDecimal taxa,

        @Schema(description = "Tipo de operação sobre a base ou definição direta", example = "DEFINIR_TAXA")
        TipoOperacaoBase tipoOperacao,

        @Schema(description = "Valor de ajuste sobre a base quando aplicável", example = "0.0150")
        BigDecimal valorAjuste
) {}
