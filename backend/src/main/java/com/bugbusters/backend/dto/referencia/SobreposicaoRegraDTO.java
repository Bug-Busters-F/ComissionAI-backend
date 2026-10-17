package com.bugbusters.backend.dto.referencia;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Diagnóstico consolidado de sobreposição entre regras da campanha em uma mesma venda")
public record SobreposicaoRegraDTO(
        @Schema(description = "Identificadores e nomes das regras envolvidas na sobreposição")
        List<String> regrasEnvolvidas,

        @Schema(description = "Quantidade total de vendas atingidas conjuntamente por essas regras", example = "42")
        int totalVendasAfetadas,

        @Schema(description = "Tipo de concorrência resultante (EMPATE, SOBREPOSTA, IDENTICA)", example = "SOBREPOSTA")
        String tipoConcorrencia,

        @Schema(description = "Indica se a sobreposição gera conflito/bloqueio crítico por empate na maior especificidade", example = "false")
        boolean temImpedimentoCritico,

        @Schema(description = "Mensagem explicativa sobre a prioridade ou conflito determinístico")
        String mensagem
) {}
