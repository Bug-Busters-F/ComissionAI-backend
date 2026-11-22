package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.indicador.IndicadoresResponse;
import com.bugbusters.backend.service.IndicadoresService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/indicadores")
@Tag(name = "Indicadores", description = "Indicadores agregados e itens de trabalho por perfil")
public class IndicadoresController {
    private final IndicadoresService indicadoresService;

    public IndicadoresController(IndicadoresService indicadoresService) {
        this.indicadoresService = indicadoresService;
    }

    @GetMapping
    @Operation(summary = "Consultar indicadores agregados do painel (BACK-18)", description = "Calcula os indicadores a partir das bases persistidas e retorna somente ações permitidas ao perfil autenticado.")
    public ResponseEntity<IndicadoresResponse> consultar() {
        return ResponseEntity.ok(indicadoresService.consultar());
    }
}
