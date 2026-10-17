package com.bugbusters.backend.dto.canal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Canal de venda reconhecido pela plataforma")
public record CanalResponseDTO(
        @Schema(description = "Identificador textual/código do canal", example = "ECOMMERCE")
        String codigo,

        @Schema(description = "Nome de exibição amigável do canal", example = "E-Commerce")
        String nomeExibicao,

        @Schema(description = "Descrição da atuação do canal", example = "Vendas realizadas na loja virtual e canais digitais")
        String descricao,

        @Schema(description = "Indica se o canal é considerado canal padrão/omissão", example = "false")
        boolean padrao
) {
}
