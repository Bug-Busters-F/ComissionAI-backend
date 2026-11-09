package com.bugbusters.backend.dto.pendencia;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Visão consolidada de pendências da campanha para governança e esteira.
 */
@Schema(description = "Resumo consolidado de pendências e aceites formais da campanha")
public record ResumoPendenciasCampanhaResponse(
        @Schema(description = "ID da campanha")
        Long campanhaId,

        @Schema(description = "ID da versão da campanha")
        Long campanhaVersaoId,

        @Schema(description = "Número da versão avaliada")
        Integer versaoNumero,

        @Schema(description = "Quantidade total de pendências detectadas")
        int totalPendencias,

        @Schema(description = "Quantidade total de impedimentos")
        int totalImpedimentos,

        @Schema(description = "Quantidade total de avisos")
        int totalAvisos,

        @Schema(description = "Quantidade total de pendências formalmente aceitas")
        int totalAceitas,

        @Schema(description = "Quantidade de impedimentos ainda pendentes de resolução ou aceite")
        int totalPendentesImpeditivas,

        @Schema(description = "Indica se a campanha está apta para avançar de etapa na esteira (sem impedimentos em aberto)")
        boolean aptaParaAvanco,

        @Schema(description = "Lista detalhada das pendências")
        List<ItemPendenciaResumoDTO> pendencias
) {
}

