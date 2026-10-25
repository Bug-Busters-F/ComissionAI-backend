package com.bugbusters.backend.dto.simulacao;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Página de vendas detalhadas de uma simulação com totais consolidados globais independentes da página")
public record SimulacaoVendasPaginadasDTO(
        @Schema(description = "Identificador único da simulação")
        UUID simulacaoId,

        @Schema(description = "Protocolo de auditoria da simulação")
        UUID protocolo,

        @Schema(description = "Competência histórica de referência", example = "2026-09")
        String competencia,

        @Schema(description = "Título da campanha simulada")
        String tituloCampanha,

        @Schema(description = "Dias inclusivos da campanha")
        long diasInclusivos,

        @Schema(description = "Fator multiplicador de projeção (dias ÷ 30)")
        BigDecimal fatorProjecao,

        @Schema(description = "Totais globais consolidados do Cenário 100% (independentes da página)")
        CenarioConsolidadoDTO cenario100,

        @Schema(description = "Totais globais consolidados do Cenário 80% (independentes da página)")
        CenarioConsolidadoDTO cenario80,

        @Schema(description = "Totais globais consolidados do Cenário 120% (independentes da página)")
        CenarioConsolidadoDTO cenario120,

        @Schema(description = "Totais globais consolidados do Cenário 150% (independentes da página)")
        CenarioConsolidadoDTO cenario150,

        @Schema(description = "Número da página atual (0-indexada)", example = "0")
        int paginaAtual,

        @Schema(description = "Quantidade de registros por página", example = "50")
        int tamanhoPagina,

        @Schema(description = "Total de registros encontrados no filtro", example = "400")
        long totalElementos,

        @Schema(description = "Total de páginas disponíveis", example = "8")
        int totalPaginas,

        @Schema(description = "Lista detalhada das vendas da página com memórias explicativas de cálculo individuais")
        List<VendaSimuladaDetalheDTO> vendas
) {}
