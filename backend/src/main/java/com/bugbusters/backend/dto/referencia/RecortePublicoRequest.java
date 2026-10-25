package com.bugbusters.backend.dto.referencia;

import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "Critérios para consulta e recorte da união de públicos em uma competência de referência")
public record RecortePublicoRequest(
        @Schema(description = "Competência de referência no formato YYYY-MM ou YYYY-MM-DD", example = "2026-09")
        @NotBlank(message = "A competência de referência é obrigatória")
        String competencia,

        @Schema(description = "Identificador de campanha existente para carregamento de suas regras (opcional se 'regras' for fornecido)", example = "1")
        Long campanhaId,

        @Schema(description = "Coleção de regras/propostas a serem avaliadas (opcional se 'campanhaId' for fornecido)")
        List<RegraItemRequest> regras,

        @Schema(description = "Limite máximo de vendas a serem retornadas na listagem detalhada (padrão: 500 registros)", example = "500")
        Integer limite,

        @Schema(description = "Número da página para listagem paginada (começando em 0, padrão: 0)", example = "0")
        Integer pagina,

        @Schema(description = "Filtrar no retorno apenas vendas que sofreram sobreposição de regras", example = "false")
        Boolean apenasSobrepostas,

        @Schema(description = "Filtrar no retorno apenas vendas que apresentaram impedimento de cálculo", example = "false")
        Boolean apenasComImpedimento
) {
    public RecortePublicoRequest {
        if (limite == null || limite <= 0) {
            limite = 500;
        }
        if (pagina == null || pagina < 0) {
            pagina = 0;
        }
        if (apenasSobrepostas == null) {
            apenasSobrepostas = false;
        }
        if (apenasComImpedimento == null) {
            apenasComImpedimento = false;
        }
    }
}
