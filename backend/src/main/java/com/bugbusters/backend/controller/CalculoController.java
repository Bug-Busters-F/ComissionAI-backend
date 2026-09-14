package com.bugbusters.backend.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.calculo.CalculoComissaoResponse;
import com.bugbusters.backend.dto.calculo.LogCalculoResponse;
import com.bugbusters.backend.dto.error.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController 
@RequestMapping("/api/v1")
@Tag(name = "2. Motor de Cálculo e Auditoria", description = "Processamento de comissões e histórico de logs imutáveis")
public class CalculoController {
    @Operation(summary = "Calcular comissão de uma venda", description = "Recebe uma venda, cruza com a regra ativa no canal correspondente e registra o log imutável de apuração.")
    @ApiResponse(responseCode = "200", description = "Cálculo processado com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados da venda inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/comissoes/calcular")
    public ResponseEntity<CalculoComissaoResponse> calcularComissao(@Valid @RequestBody CalculoComissaoRequest request) {
        BigDecimal taxaExemplo = new BigDecimal("0.1000");
        BigDecimal comissao = request.valorVenda().multiply(taxaExemplo).setScale(2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(new CalculoComissaoResponse(
            UUID.randomUUID(), request.matricula(), 1L, taxaExemplo, request.valorVenda(), comissao, OffsetDateTime.now()
        ));
    }

    @Operation(summary = "Consultar logs imutáveis de cálculos", description = "Recupera o histórico para auditoria e conferência financeira")
    @GetMapping("/logs-calculo")
    public ResponseEntity<List<LogCalculoResponse>> listarLogs() {
        return ResponseEntity.ok(List.of(
            new LogCalculoResponse(UUID.randomUUID(), "MATRIC-1", 1L, new BigDecimal("1000.00"), new BigDecimal("0.1000"), new BigDecimal("100.00"), OffsetDateTime.now())
        ));
    }
}
