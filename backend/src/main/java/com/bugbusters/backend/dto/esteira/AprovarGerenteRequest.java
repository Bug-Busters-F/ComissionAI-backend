package com.bugbusters.backend.dto.esteira;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requisição formal de aprovação da proposta na esteira pelo gerente comercial")
public record AprovarGerenteRequest(
        @Schema(description = "Identificação do gerente aprovador", example = "gerente.vendas@empresa.com")
        @NotBlank(message = "O identificador do gerente aprovador é obrigatório.")
        String aprovadoPor,

        @Schema(description = "Parecer ou notas do gestor sobre o aceite", example = "Proposta e simulação orçamentária avaliadas e aprovadas para publicação.")
        String parecer
) {}

