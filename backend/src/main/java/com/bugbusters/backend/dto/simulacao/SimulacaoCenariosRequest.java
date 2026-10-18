package com.bugbusters.backend.dto.simulacao;

import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Critérios para execução de simulação em sandbox com análise de sensibilidade e projeção temporal")
public record SimulacaoCenariosRequest(
        @Schema(description = "Competência histórica de referência no formato YYYY-MM", example = "2026-09")
        @NotBlank(message = "A competência de referência é obrigatória")
        String competencia,

        @Schema(description = "Identificador de campanha existente para carregamento de regras e datas (opcional se 'regras' e vigência forem fornecidos)", example = "1")
        Long campanhaId,

        @Schema(description = "Data inicial hipotética da campanha para projeção temporal (opcional se campanhaId informado)", example = "2026-11-01")
        LocalDate dataInicio,

        @Schema(description = "Data final hipotética da campanha para projeção temporal (opcional se campanhaId informado)", example = "2026-11-15")
        LocalDate dataFim,

        @Schema(description = "Coleção de regras/propostas revisadas a serem simuladas hipoteticamente (opcional se campanhaId informado)")
        List<RegraItemRequest> regras,

        @Schema(description = "Limite máximo de vendas detalhadas a retornar (padrão: 500 registros)", example = "500")
        Integer limite,

        @Schema(description = "Número da página para listagem paginada (começando em 0, padrão: 0)", example = "0")
        Integer pagina,

        @Schema(description = "Filtrar no retorno apenas vendas que tiveram alteração de taxa ou de comissão em relação ao cenário base", example = "false")
        Boolean apenasComAlteracao,

        @Schema(description = "Filtrar no retorno apenas vendas que sofreram mudança de faixa nos cenários escalados (80%, 120%, 150%)", example = "false")
        Boolean apenasComMudancaFaixa
) {
    public SimulacaoCenariosRequest {
        if (limite == null || limite <= 0) {
            limite = 500;
        }
        if (pagina == null || pagina < 0) {
            pagina = 0;
        }
        if (apenasComAlteracao == null) {
            apenasComAlteracao = false;
        }
        if (apenasComMudancaFaixa == null) {
            apenasComMudancaFaixa = false;
        }
    }
}
