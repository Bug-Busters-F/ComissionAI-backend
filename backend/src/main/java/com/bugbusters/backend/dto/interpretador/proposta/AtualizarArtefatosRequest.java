package com.bugbusters.backend.dto.interpretador.proposta;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Requisição para sincronização e atualização imediata dos artefatos explicativos após edição manual de campos")
public record AtualizarArtefatosRequest(
        @Schema(description = "Proposta de regra após as alterações manuais do usuário no formulário do frontend")
        @NotNull(message = "A proposta a ser atualizada é obrigatória")
        @Valid
        PropostaRegraDTO proposta,

        @Schema(description = "Campos que o usuário editou manualmente (ex: taxa, condicoes.codLoja). Uma taxa editada vale como taxa absoluta, sem recálculo sobre a base; os artefatos citam a edição manual.",
                example = "[\"taxa\"]")
        List<String> camposEditados
) {
    public AtualizarArtefatosRequest {
        camposEditados = camposEditados == null ? List.of() : List.copyOf(camposEditados);
    }

    public AtualizarArtefatosRequest(PropostaRegraDTO proposta) {
        this(proposta, List.of());
    }
}
