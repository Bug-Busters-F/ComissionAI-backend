package com.bugbusters.backend.dto.preparacao;

public record ResumoContagensPreparacaoDTO(
        int totalLinhas,
        int linhasValidas,
        int linhasInvalidas,
        int linhasImpeditivas,
        int linhasAdvertencia,
        int linhasAviso,
        int linhasEditadas,
        int linhasIgnoradas
) {}
