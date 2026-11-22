package com.bugbusters.backend.dto.interpretador.proposta;

import com.bugbusters.backend.model.OrigemOrcamentoCampanha;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Orçamento monetário extraído pela IA ou por inferência a partir do texto de campanha (IA-1 / BACK-8)")
public record OrcamentoExtraidoDTO(
        @Schema(description = "Valor monetário do teto orçamentário extraído", example = "50000.00")
        BigDecimal valor,

        @Schema(description = "Moeda do orçamento (padrão BRL)", example = "BRL")
        String moeda,

        @Schema(description = "Origem da extração", example = "EXTRAIDO_IA")
        OrigemOrcamentoCampanha origem,

        @Schema(description = "Trecho original textual de onde o orçamento foi extraído", example = "orçamento total de R$ 50.000,00")
        String trechoOriginal,

        @Schema(description = "Grau de confiança da extração (0.0 a 1.0)", example = "0.95")
        Double confianca,

        @Schema(description = "Indica se requer confirmação/alteração explícita pelo analista antes de simular (sempre true)", example = "true")
        boolean requerConfirmacaoExplicita
) {
    public OrcamentoExtraidoDTO(BigDecimal valor, String trechoOriginal, OrigemOrcamentoCampanha origem, Double confianca) {
        this(valor, "BRL", origem != null ? origem : OrigemOrcamentoCampanha.EXTRAIDO_IA, trechoOriginal, confianca != null ? confianca : 0.90, true);
    }
}
