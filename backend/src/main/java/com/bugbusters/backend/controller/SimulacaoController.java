package com.bugbusters.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosResponse;
import com.bugbusters.backend.service.SimulacaoCenariosService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/simulacoes")
@Tag(name = "Sandbox de Simulação Financeira", description = "Simulação determinística de impacto financeiro, cenários de sensibilidade (80%, 120%, 150%) e projeções temporais")
public class SimulacaoController {

    private final SimulacaoCenariosService service;

    public SimulacaoController(SimulacaoCenariosService service) {
        this.service = service;
    }

    @Operation(
            summary = "Executar simulação de cenários e sensibilidade financeira (POST)",
            description = """
                    Executa a simulação em sandbox pelo motor compartilhado de cálculo sobre as vendas recortadas da competência:
                    - Cenário Base: comissão calculada estritamente pela taxa contratual de tb_basecomiss (ignora outras campanhas ativas).
                    - Cenário Proposta (100%): aplicação hipotética das regras revisadas sem alterar datas históricas; se nenhuma regra aplicar, fallback na taxa base.
                    - Cenários de Sensibilidade (80%, 120%, 150%): recálculo por venda escalando o valor e reavaliando faixas monetárias dinamicamente.
                    - Projeção Temporal: cálculo pro-rata pelos dias inclusivos da campanha ÷ 30.
                    - Sem persistência em tb_resultado_calculo e sem lançamento de comissões reais.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Simulação de cenários executada com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos ou regras ausentes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/cenarios")
    public ResponseEntity<SimulacaoCenariosResponse> simularCenariosPost(
            @Valid @RequestBody SimulacaoCenariosRequest request) {
        return ResponseEntity.ok(service.simularCenarios(request));
    }

    @Operation(
            summary = "Executar simulação de cenários e sensibilidade financeira (GET)",
            description = "Atalho para simulação de cenários recebendo os parâmetros via Query String."
    )
    @ApiResponse(responseCode = "200", description = "Simulação de cenários executada com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/cenarios")
    public ResponseEntity<SimulacaoCenariosResponse> simularCenariosGet(
            @ModelAttribute SimulacaoCenariosRequest request) {
        return ResponseEntity.ok(service.simularCenarios(request));
    }
}
