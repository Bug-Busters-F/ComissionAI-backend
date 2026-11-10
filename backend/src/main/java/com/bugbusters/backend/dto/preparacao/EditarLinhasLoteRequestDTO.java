package com.bugbusters.backend.dto.preparacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record EditarLinhasLoteRequestDTO(
        String origem,
        String responsavel,
        String motivoGeral,
        List<@Valid ItemEdicaoLinhaDTO> linhas
) {
    public record ItemEdicaoLinhaDTO(
            int numeroLinha,
            @Valid @NotNull(message = "Os dados de edição da linha são obrigatórios.")
            EditarLinhaPreparacaoRequestDTO edicao
    ) {}
}
