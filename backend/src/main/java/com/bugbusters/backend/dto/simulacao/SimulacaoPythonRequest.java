package com.bugbusters.backend.dto.simulacao;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Requisição para orquestrar simulação executada via programa Python da campanha (BUG-106 / BACK-11)")
public record SimulacaoPythonRequest(
        @Schema(description = "Competência histórica de referência para extração das vendas (YYYY-MM)", example = "2026-09")
        @NotBlank(message = "A competência histórica é obrigatória.")
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "A competência deve seguir o formato YYYY-MM.")
        String competencia,

        @Schema(description = "Número específico da versão do programa Python a executar (opcional, padrão: versão aprovada)")
        Integer versaoPrograma,

        @Schema(description = "Quantidade máxima de vendas retornadas no detalhamento paginado", example = "50")
        Integer limite,

        @Schema(description = "Número da página para detalhamento (base 0)", example = "0")
        Integer pagina,

        @Schema(description = "Filtrar no detalhamento apenas vendas com alteração financeira em relação à base")
        Boolean apenasComAlteracao,

        @Schema(description = "Filtrar no detalhamento apenas vendas com transição de faixa monetária")
        Boolean apenasComMudancaFaixa
) {}

