package com.bugbusters.backend.dto.pendencia;

import com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Requisição para formalizar o aceite rastreável de uma pendência ou risco de regra/campanha.
 */
@Schema(description = "Dados para aceite rastreável de pendência ou inconsistência")
public record AceitarPendenciaRequest(

        @Schema(description = "ID opcional da regra associada à pendência", example = "10")
        Long regraId,

        @Schema(description = "Identificador estável da pendência (ID gerado ou código)", example = "artefatos:aviso:one")
        @NotBlank(message = "O identificador da pendência é obrigatório.")
        String pendenciaIdentificador,

        @Schema(description = "Código de classificação da pendência", example = "LOJA_FORA_CATALOGO")
        @NotBlank(message = "O código da pendência é obrigatório.")
        String codigo,

        @Schema(description = "Campo impactado", example = "filtros.codLoja")
        String campo,

        @Schema(description = "Mensagem descritiva da inconsistência", example = "Loja 99 não encontrada no cadastro atual.")
        String mensagem,

        @Schema(description = "Severidade da pendência (AVISO ou IMPEDIMENTO)", example = "IMPEDIMENTO")
        SeveridadePendencia severidade,

        @Schema(description = "Identificação do responsável ou gestor que assume o aceite do risco", example = "gestor.comissoes@empresa.com")
        @NotBlank(message = "A identificação do responsável pelo aceite é obrigatória.")
        String aceitoPor,

        @Schema(description = "Justificativa formal do aceite e medidas de mitigação", example = "Loja inaugurada na semana corrente; inclusão formal em andamento no ERP.")
        @NotBlank(message = "A justificativa formal do aceite é obrigatória.")
        String motivo
) {
}

