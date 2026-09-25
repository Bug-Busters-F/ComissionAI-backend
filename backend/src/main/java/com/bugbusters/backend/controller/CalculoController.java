package com.bugbusters.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.calculo.CalculoComissaoResponse;
import com.bugbusters.backend.dto.calculo.CalculoCompetenciaRequest;
import com.bugbusters.backend.dto.calculo.CalculoCompetenciaResponseDTO;
import com.bugbusters.backend.dto.calculo.CalculoIndividualResponseDTO;
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
@Tag(name = "2. Motor de Cálculo e Auditoria", description = "Processamento de comissões individuais e por competência, com histórico de logs imutáveis")
public class CalculoController {

    private final CalculoService calculoService;

    public CalculoController(CalculoService calculoService) {
        this.calculoService = calculoService;
    }

    @Operation(
            summary = "Calcular comissão de venda individual persistida (S1-B07)",
            description = """
                    Recebe o identificador UUID de uma venda previamente persistida (S1-B07), valida os vínculos essenciais
                    do colaborador (matrícula, vigência contratual e cargo) e seleciona a taxa de comissão conforme S1-B08
                    (prioridade na taxa de BaseComiss por Marca+Cargo e fallback em Regra de negócio ativa).
                    Aplica arredondamento bancário HALF_UP com precisão decimal em BigDecimal (escala 2), persiste os resultados
                    e registra o log imutável de auditoria (S1-B10).
                    Retorna o resultado de sucesso ou o motivo contratual de impedimento.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Venda processada (status SUCESSO ou IMPEDIDO)")
    @ApiResponse(responseCode = "400", description = "ID da venda inválido, não encontrada ou divergente de apuração prévia", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/comissoes/calcular/venda/{id}")
    public ResponseEntity<CalculoIndividualResponseDTO> calcularVendaIndividual(@PathVariable("id") UUID id) {
        CalculoIndividualResponseDTO response = calculoService.calcularVendaIndividualPorId(id);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Calcular conjunto de comissões por competência mensal",
            description = """
                    Processa o conjunto de todas as vendas efetivadas pertencentes à competência solicitada (ex: '2026-09').
                    Para cada venda, cruza com os vínculos de RH (matrícula ativa, cargo, loja e vigência), seleciona a taxa
                    aplicável em BaseComiss (S1-B08) ou Regra ativa, calcula a comissão com tipo decimal e precisão
                    arredondada (HALF_UP), grava resultados e logs imutáveis (S1-B10) com garantia de idempotência.
                    Retorna a consolidação do fechamento com a lista de vendas calculadas com sucesso e as vendas com impedimento.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Processamento da competência executado com sucesso")
    @ApiResponse(responseCode = "400", description = "Formato de competência inválido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/comissoes/calcular-competencia")
    public ResponseEntity<CalculoCompetenciaResponseDTO> calcularPorCompetencia(@Valid @RequestBody CalculoCompetenciaRequest request) {
        CalculoCompetenciaResponseDTO response = calculoService.calcularPorCompetencia(request.competencia());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Calcular comissão de uma venda (Payload Avulso)", description = "Recebe dados manuais de uma venda, cruza com a regra ativa e registra o log imutável de apuração com proteção contra cálculo duplicado.")
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
