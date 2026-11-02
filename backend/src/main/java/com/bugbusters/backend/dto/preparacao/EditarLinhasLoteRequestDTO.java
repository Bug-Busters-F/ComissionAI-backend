package com.bugbusters.backend.dto.preparacao;

import java.util.List;

public record EditarLinhasLoteRequestDTO(
        String origem,
        String responsavel,
        String motivoGeral,
        List<ItemEdicaoLinhaDTO> linhas
) {
    public record ItemEdicaoLinhaDTO(
            int numeroLinha,
            EditarLinhaPreparacaoRequestDTO edicao
    ) {}
}
