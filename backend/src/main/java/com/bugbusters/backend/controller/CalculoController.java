package com.bugbusters.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.calculo.CalculoComissaoResponse;
import com.bugbusters.backend.dto.calculo.LogCalculoResponse;
import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.service.CalculoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController 
@RequestMapping("/api/v1")
@Tag(name = "2. Motor de Cálculo e Auditoria", description = "Processamento de comissões e histórico de logs imutáveis")
public class CalculoController {

    private final CalculoService calculoService;

    public CalculoController(CalculoService calculoService) {
        this.calculoService = calculoService;
    }

    @Operation(summary = "Calcular comissão de uma venda", description = "Recebe uma venda, cruza com a regra ativa no canal correspondente e registra o log imutável de apuração com proteção contra cálculo duplicado.")
    @ApiResponse(responseCode = "200", description = "Cálculo processado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados da venda inválidos ou divergentes de cálculo prévio", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/comissoes/calcular")
    public ResponseEntity<CalculoComissaoResponse> calcularComissao(@Valid @RequestBody CalculoComissaoRequest request) {
        CalculoComissaoResponse response = calculoService.calcularComissao(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Consultar logs imutáveis de cálculos", description = "Recupera o histórico para auditoria e conferência financeira")
    @GetMapping("/logs-calculo")
    public ResponseEntity<List<LogCalculoResponse>> listarLogs() {
        return ResponseEntity.ok(calculoService.listarLogs());
    }
}

