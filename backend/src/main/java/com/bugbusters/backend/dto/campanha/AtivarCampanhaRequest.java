package com.bugbusters.backend.dto.campanha;

import io.swagger.v3.oas.annotations.media.Schema;

public record AtivarCampanhaRequest(
        @Schema(description = "Versão vigente esperada. Obrigatória para publicar uma revisão; opcional na primeira ativação.")
        Integer versaoEsperada
) {}
