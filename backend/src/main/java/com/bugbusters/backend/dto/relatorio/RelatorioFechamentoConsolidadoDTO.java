package com.bugbusters.backend.dto.relatorio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Relatório consolidado e completo de fechamento oficial de comissões (BUG-110 / BACK-15)")
public record RelatorioFechamentoConsolidadoDTO(
        @Schema(description = "Resumo executivo do fechamento")
        ResumoFechamentoDTO resumo,

        @Schema(description = "Consolidação dos resultados por canal de venda")
        List<ConsolidadoCanalDTO> porCanal,

        @Schema(description = "Consolidação dos resultados por equipe (loja e marca)")
        List<ConsolidadoEquipeDTO> porEquipe,

        @Schema(description = "Consolidação dos resultados individuais por colaborador/vendedor")
        List<ConsolidadoColaboradorDTO> porColaborador,

        @Schema(description = "Listagem detalhada das vendas liquidadas")
        List<ItemRelatorioFechamentoDTO> detalheVendas
) {}

