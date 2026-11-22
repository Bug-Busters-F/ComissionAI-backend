package com.bugbusters.backend.controller;
import com.bugbusters.backend.dto.simulacao.*;
import com.bugbusters.backend.dto.programapython.ProgramaPythonResponse;
import com.bugbusters.backend.service.AplicacaoCalibracaoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/simulacoes/{id}")
public class CalibracaoSimulacaoController {
    private final AplicacaoCalibracaoService service;
    public CalibracaoSimulacaoController(AplicacaoCalibracaoService service) { this.service=service; }
    @GetMapping("/analise-python")
    public AnaliseSimulacaoDTO consultar(@PathVariable UUID id) { return service.consultar(id); }
    @PostMapping("/calibracoes/aplicar")
    public ProgramaPythonResponse aplicar(@PathVariable UUID id,@Valid @RequestBody AplicarCalibracaoRequest request) {return service.aplicar(id,request);}
}
