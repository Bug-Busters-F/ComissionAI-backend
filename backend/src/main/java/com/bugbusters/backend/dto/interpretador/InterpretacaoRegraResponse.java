package com.bugbusters.backend.dto.interpretador;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Proposta estruturada extraída pela IA com pendências apontadas")
public record InterpretacaoRegraResponse(
        @Schema(description = "Canal padronizado identificado (ex: LOJA_FISICA, ECOMMERCE)", example = "LOJA_FISICA")
        String canal,

        @Schema(description = "Código da marca identificada (ex: 10, 20, 30, 40)", example = "10")
        Integer codMarca,

        @Schema(description = "Descrição textual da marca (ex: PRETO, BRANCO, AZUL)", example = "PRETO")
        String descrMarca,

        @Schema(description = "Código numérico do cargo (ex: 100, 150, 200)", example = "100")
        Integer codCargo,

        @Schema(description = "Descrição textual do cargo (ex: VENDEDOR LOJA, GERENTE)", example = "VENDEDOR LOJA")
        String descriCargo,

        @Schema(description = "Código numérico da loja (ex: 75, 35)", example = "75")
        Integer codLoja,

        @Schema(description = "Taxa percentual em formato decimal (0.0500 = 5%)", example = "0.0500")
        BigDecimal taxa,

        @Schema(description = "Data de início da vigência inferida", example = "2026-12-01")
        LocalDate dataInicio,

        @Schema(description = "Data de fim da vigência inferida", example = "2026-12-31")
        LocalDate dataFim,

        @Schema(description = "Score de confiança do modelo (0.00 a 1.00)", example = "0.95")
        BigDecimal confianca,

        @Schema(description = "Lista de pendências, campos faltantes ou inconsistências que exigem revisão humana")
        List<String> pendencias
) {
    public InterpretacaoRegraResponse(String canal, BigDecimal taxa, LocalDate dataInicio, LocalDate dataFim, BigDecimal confianca, List<String> pendencias) {
        this(canal, null, null, null, null, null, taxa, dataInicio, dataFim, confianca, pendencias);
    }
}