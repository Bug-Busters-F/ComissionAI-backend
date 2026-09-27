package com.bugbusters.backend.dto.interpretador;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Entrada de linguagem natural e contexto para o serviço de interpretação")
public record InterpretacaoRegraRequest(
    @Schema(description = "Comando em texto livre digitado pelo gestor", example = "Pagar 5% no canal ecommerce durante dezembro")
    @NotBlank(message = "O texto do comando é obrigatório")
    String texto,

    @Schema(description = "Contexto adicional de negócio (metadados, marca, competência)", example = "{\"canal_padrao\": \"ECOMMERCE\", \"ano_referencia\": 2026}")
    Map<String, Object> contexto
) {}
