package com.bugbusters.backend.dto.interpretador.proposta;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Contrato estruturado de um bloco de proposta de regra, compartilhado entre frontend, backend e IA")
public record PropostaRegraDTO(
        @Schema(description = "Identificador único do bloco dentro da proposta", example = "bloco-1")
        @NotBlank(message = "O identificador do bloco é obrigatório")
        String blocoId,

        @Schema(description = "Trecho específico do texto em linguagem natural correspondente a este bloco", example = "Comissão de 4.5% no canal ecommerce para vendas acima de R$ 5.000")
        String trechoOrigem,

        @Schema(description = "Filtros relacionais de público-alvo (canal, marca, loja, cargo, matrícula)")
        @NotNull(message = "Os filtros relacionais são obrigatórios")
        @Valid
        FiltrosRegraDTO filtros,

        @Schema(description = "Condições e faixas de valor monetário de venda com suporte a intervalos inclusivos ou exclusivos")
        @Valid
        FaixaValorDTO condicaoValor,

        @Schema(description = "Operação de cálculo realizada sobre a taxa base contratual ou definição direta")
        @NotNull(message = "A especificação da operação sobre a base é obrigatória")
        @Valid
        OperacaoBaseDTO operacaoBase,

        @Schema(description = "Taxa percentual efetiva resultante em formato decimal com escala 4 (ex: 0.0450 = 4.50%)", example = "0.0450")
        BigDecimal taxaFinal,

        @Schema(description = "Lista de referências consultadas nos cadastros do sistema durante a interpretação")
        List<ReferenciaConsultadaDTO> referenciasConsultadas,

        @Schema(description = "Mapeamento de rastreabilidade campo a campo indicando como cada valor foi inferido")
        Map<String, OrigemCampo> origensPorCampo,

        @Schema(description = "Lista de pendências, avisos ou divergências apontadas que exigem revisão humana")
        List<PendenciaPropostaDTO> pendencias,

        @Schema(description = "Indica se a proposta possui todos os campos obrigatórios válidos ou se é incompleta (permitida como rascunho)", example = "true")
        Boolean completa,

        @Schema(description = "Artefato explicativo em linguagem natural justificando o cálculo e condições da regra (XAI)", example = "Aplica taxa de 4.50% para vendas no canal ECOMMERCE com valor superior a R$ 5.000,00.")
        String explicacao,

        @Schema(description = "Artefato de código em Python equivalente representando a função determinística da regra para simulações", example = "def calcular_comissao(venda): ...")
        String pythonEquivalente,

        @Schema(description = "Período temporal de vigência da proposta")
        @Valid
        PeriodoVigenciaDTO vigencia
) {
    public PropostaRegraDTO {
        if (referenciasConsultadas == null) {
            referenciasConsultadas = List.of();
        }
        if (origensPorCampo == null) {
            origensPorCampo = Map.of();
        }
        if (pendencias == null) {
            pendencias = List.of();
        }
        if (condicaoValor == null) {
            condicaoValor = FaixaValorDTO.semLimites();
        }
        if (filtros == null) {
            filtros = FiltrosRegraDTO.vazio();
        }
        if (completa == null) {
            completa = pendencias.stream().noneMatch(PendenciaPropostaDTO::isImpeditiva);
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean possuiImpedimento() {
        return pendencias != null && pendencias.stream().anyMatch(PendenciaPropostaDTO::isImpeditiva);
    }
}
