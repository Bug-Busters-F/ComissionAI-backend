package com.bugbusters.backend.dto.simulacao;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Dados para registro do aceite formal de uma simulação de cenários")
public record AceiteSimulacaoRequest(
        @Schema(description = "Identificação do usuário ou gestor responsável pelo aceite", example = "gestor.comissoes@empresa.com")
        @NotBlank(message = "A identificação do responsável pelo aceite é obrigatória.")
        String aceitoPor,

        @Schema(description = "Justificativa de negócio ou observações sobre o aceite da proposta simulada", example = "Simulação aprovada pela diretoria comercial para a campanha de Black Friday.")
        String motivo,

        @Schema(description = "Identificador opcional da campanha a ser vinculada a esta simulação", example = "1")
        Long campanhaId
) {
    public AceiteSimulacaoRequest(String aceitoPor, String motivo) {
        this(aceitoPor, motivo, null);
    }
}

