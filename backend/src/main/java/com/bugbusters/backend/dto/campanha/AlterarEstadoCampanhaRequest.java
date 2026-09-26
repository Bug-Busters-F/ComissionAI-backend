package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.model.EstadoCampanha;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dados para alteração do estado da campanha")
public record AlterarEstadoCampanhaRequest(
        @Schema(description = "Novo estado da campanha (DRAFT, ATIVA, INATIVA, CONCLUIDA, CANCELADA)", example = "ATIVA")
        @NotNull(message = "O estado da campanha é obrigatório")
        EstadoCampanha estado
) {
}
