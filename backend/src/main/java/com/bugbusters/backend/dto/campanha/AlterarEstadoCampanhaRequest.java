package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.model.EstadoCampanha;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Dados para alteração do estado da campanha")
public record AlterarEstadoCampanhaRequest(
        @Schema(description = "Novo estado da campanha (DRAFT, ATIVA, INATIVA, CONCLUIDA, CANCELADA)", example = "ATIVA")
        @NotNull(message = "O estado da campanha é obrigatório")
        EstadoCampanha estado,

        @Schema(description = "Identificador opcional da simulação aceita a vincular", example = "550e8400-e29b-41d4-a716-446655440000")
        java.util.UUID simulacaoAceitaId,

        @Schema(description = "Identificação de quem aceitou a simulação", example = "gestor.comissoes@empresa.com")
        String aceitoPor,

        @Schema(description = "Motivo ou justificativa do aceite", example = "Aprovado em comitê")
        String motivoAceite,

        @Schema(description = "Versão vigente esperada ao publicar uma revisão")
        Integer versaoEsperada
) {
    public AlterarEstadoCampanhaRequest(EstadoCampanha estado) {
        this(estado, null, null, null, null);
    }

    public AlterarEstadoCampanhaRequest(EstadoCampanha estado, java.util.UUID simulacaoAceitaId, String aceitoPor, String motivoAceite) {
        this(estado, simulacaoAceitaId, aceitoPor, motivoAceite, null);
    }
}
