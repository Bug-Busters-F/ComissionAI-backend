package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraRequest;
import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraResponse;
import com.bugbusters.backend.service.InterpretadorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/interpretador")
@Tag(name = "4. Assistência por IA (NLP)", description = "Integração do Spring Boot ao serviço de interpretação Python")
public class InterpretadorController {

    private final InterpretadorService interpretadorService;

    public InterpretadorController(InterpretadorService interpretadorService) {
        this.interpretadorService = interpretadorService;
    }

    @Operation(summary = "Interpretar regra em linguagem natural", 
               description = "Envia comando em texto e contexto para o serviço Python, valida os campos no Spring e devolve a sugestão com pendências para o front-end.")
    @ApiResponse(responseCode = "200", description = "Texto interpretado e validado")
    @ApiResponse(responseCode = "400", description = "Comando vazio ou inválido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Serviço de IA Python indisponível", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "504", description = "Tempo limite excedido na inferência", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/extrair-regra")
    public ResponseEntity<InterpretacaoRegraResponse> interpretarRegra(@Valid @RequestBody InterpretacaoRegraRequest request) {
        return ResponseEntity.ok(interpretadorService.processarInterpretacao(request));
    }
}