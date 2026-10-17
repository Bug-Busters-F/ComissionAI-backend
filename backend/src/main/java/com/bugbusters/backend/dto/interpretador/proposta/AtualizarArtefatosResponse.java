package com.bugbusters.backend.dto.interpretador.proposta;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta contendo artefatos explicativos sincronizados e taxa recalculada após edição manual")
public record AtualizarArtefatosResponse(
        @Schema(description = "Identificador do bloco atualizado", example = "bloco-1")
        String blocoId,

        @Schema(description = "Taxa percentual resultante recalculada deterministicamente (escala 4)", example = "0.0450")
        BigDecimal taxaCalculada,

        @Schema(description = "Artefato textual explicativo em linguagem natural atualizado (XAI)", example = "Aplica taxa de 4.50% para vendas no canal ECOMMERCE...")
        String explicacao,

        @Schema(description = "Artefato de código em Python equivalente atualizado para a regra", example = "def calcular_comissao(venda): ...")
        String pythonEquivalente,

        @Schema(description = "Lista atualizada de pendências e validações da proposta")
        List<PendenciaPropostaDTO> pendencias,

        @Schema(description = "Indica se a proposta está completa após a edição", example = "true")
        Boolean completa
) {
    public AtualizarArtefatosResponse {
        if (pendencias == null) {
            pendencias = List.of();
        }
        if (completa == null) {
            completa = pendencias.stream().noneMatch(PendenciaPropostaDTO::isImpeditiva);
        }
    }
}
