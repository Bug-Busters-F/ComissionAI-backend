package com.bugbusters.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.dto.regra.ConsultaConcorrenciaRequest;
import com.bugbusters.backend.dto.regra.ConsultaConcorrenciaResponse;
import com.bugbusters.backend.service.RegraConcorrenciaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/regras/concorrentes")
@Tag(name = "Regras Concorrentes", description = "Consulta e análise de concorrência e precedência entre regras de comissão ativas")
public class RegraConcorrenciaController {

    private final RegraConcorrenciaService service;

    public RegraConcorrenciaController(RegraConcorrenciaService service) {
        this.service = service;
    }

    @Operation(
            summary = "Analisar concorrência de regras (POST)",
            description = """
                Analisa potenciais regras concorrentes para uma proposta de regra com base em seu período
                e dimensões de público (marca, loja, cargo, canal e matrícula).
                Retorna os pesos de especificidade, tipo de sobreposição (EMPATE, IDENTICA, SOBREPOSTA) e
                mensagem explicativa personalizada para cada caso.
                """
    )
    @ApiResponse(responseCode = "200", description = "Análise de concorrência realizada com sucesso")
    @PostMapping
    public ResponseEntity<ConsultaConcorrenciaResponse> analisarConcorrenciaPost(
            @RequestBody ConsultaConcorrenciaRequest request) {
        return ResponseEntity.ok(service.analisarConcorrencia(request));
    }

    @Operation(
            summary = "Analisar concorrência de regras (GET)",
            description = "Mesma funcionalidade do endpoint POST, permitindo envio dos parâmetros via Query String."
    )
    @ApiResponse(responseCode = "200", description = "Análise de concorrência realizada com sucesso")
    @GetMapping
    public ResponseEntity<ConsultaConcorrenciaResponse> analisarConcorrenciaGet(
            @ModelAttribute ConsultaConcorrenciaRequest request) {
        return ResponseEntity.ok(service.analisarConcorrencia(request));
    }
}
