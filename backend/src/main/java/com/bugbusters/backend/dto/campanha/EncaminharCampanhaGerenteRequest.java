package com.bugbusters.backend.dto.campanha;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Requisição para encaminhamento da campanha revisada e simulada ao gerente")
public record EncaminharCampanhaGerenteRequest(
        @Schema(description = "Identificador opcional da simulação aceita/enquadrada (se omitido, usa a vinculada à proposta)")
        UUID simulacaoId,

        @Schema(description = "Identificação de quem está encaminhando a campanha ao gerente", example = "analista.comissoes@empresa.com")
        String encaminhadoPor,

        @Schema(description = "Observação ou parecer técnico para a revisão do gerente", example = "Campanha simulada e 100% enquadrada dentro do teto orçamentário.")
        String observacao,

        @Schema(description = "Versão esperada da campanha para controle de concorrência otimista", example = "1")
        Integer versaoEsperada
) {
    public EncaminharCampanhaGerenteRequest(String encaminhadoPor, String observacao) {
        this(null, encaminhadoPor, observacao, null);
    }
}
