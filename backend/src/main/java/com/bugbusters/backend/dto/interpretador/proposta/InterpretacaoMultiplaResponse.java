package com.bugbusters.backend.dto.interpretador.proposta;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Conjunto consolidado de propostas de regras interpretadas a partir de texto livre com rastreabilidade")
public record InterpretacaoMultiplaResponse(
        @Schema(description = "Título sugerido para a campanha com base nas regras interpretadas", example = "Campanha Outubro Especial E-commerce e Loja 75")
        String tituloSugerido,

        @Schema(description = "Lista estruturada dos blocos de regras propostos")
        List<PropostaRegraDTO> propostas,

        @Schema(description = "Quantidade total de blocos identificados", example = "2")
        Integer quantidadeBlocos,

        @Schema(description = "Indica se há pendências (avisos ou impedimentos) que requerem atenção do usuário", example = "true")
        Boolean possuiPendencias,

        @Schema(description = "Indica se todas as propostas estão completas e sem pendências impeditivas", example = "false")
        Boolean todasCompletas,

        @Schema(description = "Nível de confiança consolidado do modelo na extração (0.00 a 1.00)", example = "0.92")
        BigDecimal confiancaGeral,

        @Schema(description = "Pendências que não pertencem a uma proposta: falha ou limite do agente e trechos do texto que não viraram regra")
        List<PendenciaPropostaDTO> pendenciasGerais
) {
    public InterpretacaoMultiplaResponse(String tituloSugerido, List<PropostaRegraDTO> propostas, Integer quantidadeBlocos,
                                         Boolean possuiPendencias, Boolean todasCompletas, BigDecimal confiancaGeral) {
        this(tituloSugerido, propostas, quantidadeBlocos, possuiPendencias, todasCompletas, confiancaGeral, null);
    }

    public InterpretacaoMultiplaResponse {
        if (propostas == null) {
            propostas = List.of();
        }
        if (quantidadeBlocos == null) {
            quantidadeBlocos = propostas.size();
        }
        if (pendenciasGerais == null) {
            pendenciasGerais = List.of();
        }
        if (possuiPendencias == null) {
            possuiPendencias = !pendenciasGerais.isEmpty() || propostas.stream().anyMatch(p -> p.pendencias() != null && !p.pendencias().isEmpty());
        }
        if (todasCompletas == null) {
            boolean temImpeditivaGeral = pendenciasGerais.stream().anyMatch(PendenciaPropostaDTO::isImpeditiva);
            todasCompletas = !propostas.isEmpty() && !temImpeditivaGeral && propostas.stream().allMatch(p -> Boolean.TRUE.equals(p.completa()));
        }
    }
}
