package com.bugbusters.backend.dto.interpretador.proposta;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Requisição para reinterpretação localizada de um bloco de proposta específico")
public record ReinterpretarBlocoRequest(
        @Schema(description = "Identificador do bloco alvo da reinterpretação", example = "bloco-2")
        @NotBlank(message = "O identificador do bloco alvo é obrigatório")
        String blocoId,

        @Schema(description = "Nova instrução ou texto refinado para ajustar o bloco especificado", example = "No bloco 2, mudar a condição para vendas acima de R$ 10.000 e taxa base + 2.0%")
        @NotBlank(message = "A instrução de ajuste é obrigatória")
        String instrucaoAjuste,

        @Schema(description = "Estado atual da lista completa de propostas para preservação de contexto dos demais blocos")
        @NotNull(message = "A lista de propostas atuais é obrigatória")
        List<PropostaRegraDTO> propostasAtuais,

        @Schema(description = "Contexto adicional do negócio")
        Map<String, Object> contexto
) {
    public ReinterpretarBlocoRequest {
        if (propostasAtuais == null) {
            propostasAtuais = List.of();
        }
        if (contexto == null) {
            contexto = Map.of();
        }
    }
}
