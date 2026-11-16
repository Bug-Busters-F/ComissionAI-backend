package com.bugbusters.backend.dto.relatorio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Relatório consolidado e completo de fechamento oficial de comissões (FRONT-11 / BACK-15)")
public record RelatorioFechamentoConsolidadoDTO(
        @Schema(description = "Competência no formato YYYY-MM", example = "2026-10")
        String competencia,

        @Schema(description = "Identificador único do fechamento concluído")
        UUID fechamentoId,

        @Schema(description = "Protocolo oficial de liquidação")
        UUID protocolo,

        @Schema(description = "Data/hora de conclusão do fechamento")
        OffsetDateTime fechadoEm,

        @Schema(description = "Responsável pela execução do fechamento")
        String executadoPor,

        @Schema(description = "Justificativa/motivo formal do fechamento")
        String motivo,

        @Schema(description = "Totais consolidados gerais da competência")
        TotaisGeraisFechamentoDTO totaisGerais,

        @Schema(description = "Totais agregados por regra ou origem de taxa aplicada")
        List<TotalPorRegraDTO> totaisPorRegra,

        @Schema(description = "Totais agregados por colaborador")
        List<TotalPorColaboradorDTO> totaisPorColaborador,

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
) {
    public RelatorioFechamentoConsolidadoDTO(
            ResumoFechamentoDTO resumo,
            List<ConsolidadoCanalDTO> porCanal,
            List<ConsolidadoEquipeDTO> porEquipe,
            List<ConsolidadoColaboradorDTO> porColaborador,
            List<ItemRelatorioFechamentoDTO> detalheVendas
    ) {
        this(
                resumo != null ? resumo.competencia() : null,
                null,
                resumo != null ? resumo.protocolo() : null,
                resumo != null ? resumo.executadoEm() : null,
                resumo != null ? resumo.executadoPor() : null,
                null,
                resumo != null ? new TotaisGeraisFechamentoDTO(
                        resumo.totalVendasProcessadas(),
                        resumo.totalCalculados(),
                        resumo.totalImpedimentos(),
                        resumo.valorTotalVendas(),
                        resumo.valorTotalComissao()
                ) : null,
                List.of(),
                List.of(),
                resumo,
                porCanal,
                porEquipe,
                porColaborador,
                detalheVendas
        );
    }
}

