package com.bugbusters.backend.dto.interpretador.proposta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nível de severidade de uma pendência apontada na proposta de regra")
public enum SeveridadePendencia {
    @Schema(description = "Aviso não impeditivo (ex: vigência assumida por padrão). Permite salvar como rascunho.")
    AVISO,

    @Schema(description = "Pendência impeditiva (ex: taxa ausente, divisão por zero, data final anterior à inicial). Bloqueia ativação.")
    IMPEDIMENTO
}
