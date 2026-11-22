package com.bugbusters.backend.dto.programapython;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload para submissão ou atualização do código executável em Python da campanha")
public record AtualizarProgramaPythonRequest(
        @NotBlank(message = "O código-fonte do programa Python é obrigatório.")
        @Schema(description = "Código-fonte Python completo para execução da campanha", example = "def calcular_comissao(venda: dict) -> float | None:\n    return float(venda.get('valor_venda', 0)) * 0.05")
        String codigoFonte,

        @Schema(description = "Identificação do autor ou serviço que gerou o código", example = "Serviço IA / Revisor Técnico")
        String autor,

        @Schema(description = "Justificativa ou descrição do ajuste efetuado no código", example = "Ajuste na condição de canal e faixa mínima")
        String justificativa
) {
}

