package com.bugbusters.backend.dto.fechamento;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Critérios para execução formal da apuração e fechamento final da competência (BACK-11)")
public record FechamentoRequestDTO(
        @Schema(description = "Competência mensal a ser fechada no formato YYYY-MM", example = "2026-10")
        @NotBlank(message = "A competência é obrigatória para o fechamento.")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Formato de competência inválido. Utilize YYYY-MM (ex: 2026-10).")
        String competencia,

        @Schema(description = "Identificação do usuário ou setor responsável pelo fechamento", example = "controladoria@empresa.com")
        String executadoPor,

        @Schema(description = "Justificativa ou observações sobre o fechamento contábil", example = "Fechamento mensal oficial de apuração com regras vigentes.")
        String motivo
) {
    public FechamentoRequestDTO {
        if (executadoPor == null || executadoPor.isBlank()) {
            executadoPor = "SISTEMA";
        }
    }
}
