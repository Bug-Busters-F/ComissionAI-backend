package com.bugbusters.backend.dto.pendencia;

import com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Representação de uma pendência no contexto da campanha com seu estado de aceite formal.
 */
@Schema(description = "Item de pendência com situação de aceite rastreável")
public record ItemPendenciaResumoDTO(
        @Schema(description = "Identificador estável da pendência")
        String id,

        @Schema(description = "ID da regra vinculada ou nulo se for pendência de campanha")
        Long regraId,

        @Schema(description = "Nome descritivo da regra")
        String regraNome,

        @Schema(description = "Código de identificação da inconsistência")
        String codigo,

        @Schema(description = "Campo associado")
        String campo,

        @Schema(description = "Mensagem explicativa da inconsistência")
        String mensagem,

        @Schema(description = "Severidade da pendência (AVISO ou IMPEDIMENTO)")
        SeveridadePendencia severidade,

        @Schema(description = "Indica se esta pendência possui aceite formal ativo")
        boolean aceita,

        @Schema(description = "Detalhes do aceite formal, caso já tenha sido aceita")
        PendenciaAceiteResponse aceite
) {
}

