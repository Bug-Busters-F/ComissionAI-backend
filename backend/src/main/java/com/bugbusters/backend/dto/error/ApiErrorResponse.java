package com.bugbusters.backend.dto.error;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estrutura padrão para respostas de erro da API")
public record ApiErrorResponse(
    @Schema(description = "Timestamp UTC do erro", example = "2026-09-08T22:15:30Z")
    OffsetDateTime timestamp,

    @Schema(description = "Código HTTP de status", example = "400")
    int status,

    @Schema(description = "Classificação resumida do erro", example = "Bad Request")
    String error,

    @Schema(description = "Mensagem amigável de orientação", example = "Dados de entrada inválidos.")
    String message,

    @Schema(description = "Caminho da URI que gerou o erro", example = "/api/v1/campanhas")
    String path,
    
    @Schema(description = "Detalhamento por campo (quando houver falhas de validação)")
    List<CampoInvalidoDTO> validacoes
) {
    public record CampoInvalidoDTO(String campo, String motivo) {}
}
