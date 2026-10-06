package com.bugbusters.backend.dto.interpretador.proposta;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Inconsistência, dúvida ou campo pendente que exige revisão humana")
public record PendenciaPropostaDTO(
        @Schema(description = "Campo ou dimensão afetada pela pendência", example = "taxaFinal")
        String campo,

        @Schema(description = "Código de identificação da pendência", example = "TAXA_AUSENTE")
        String codigo,

        @Schema(description = "Mensagem orientativa em linguagem amigável", example = "Percentual de comissão não identificado no texto.")
        String mensagem,

        @Schema(description = "Classificação de severidade da pendência (AVISO ou IMPEDIMENTO)", example = "IMPEDIMENTO")
        SeveridadePendencia severidade
) {
    public static PendenciaPropostaDTO impedimento(String campo, String codigo, String mensagem) {
        return new PendenciaPropostaDTO(campo, codigo, mensagem, SeveridadePendencia.IMPEDIMENTO);
    }

    public static PendenciaPropostaDTO aviso(String campo, String codigo, String mensagem) {
        return new PendenciaPropostaDTO(campo, codigo, mensagem, SeveridadePendencia.AVISO);
    }

    @JsonIgnore
    public boolean isImpeditiva() {
        return severidade == SeveridadePendencia.IMPEDIMENTO;
    }
}
