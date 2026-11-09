package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoPythonRequest;
import com.bugbusters.backend.service.SimulacaoPythonOrquestradorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campanhas/{id}")
@Tag(name = "Orquestração de Simulações Python", description = "Execução e persistência de simulações com o código Python da campanha (BUG-106 / BACK-11)")
public class SimulacaoPythonController {

    private final SimulacaoPythonOrquestradorService orquestradorService;

    public SimulacaoPythonController(SimulacaoPythonOrquestradorService orquestradorService) {
        this.orquestradorService = orquestradorService;
    }

    @Operation(summary = "Executar e persistir simulação com o programa Python da campanha (BUG-106 / BACK-11)",
            description = "Orquestra a simulação multi-cenário executando o código Python versionado da campanha, persistindo em tb_simulacao com rastreabilidade total.")
    @ApiResponse(responseCode = "201", description = "Simulação executada e persistida com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos ou campanha sem regras/código", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/simulacao-python")
    public ResponseEntity<SimulacaoCenariosResponse> simularPython(
            @PathVariable Long id,
            @Valid @RequestBody SimulacaoPythonRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orquestradorService.orquestrarEPersistir(id, null, request));
    }

    @Operation(summary = "Executar e persistir simulação com programa Python para uma versão específica da campanha")
    @PostMapping("/versoes/{versao}/simulacao-python")
    public ResponseEntity<SimulacaoCenariosResponse> simularPythonPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao,
            @Valid @RequestBody SimulacaoPythonRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orquestradorService.orquestrarEPersistir(id, versao, request));
    }
}

