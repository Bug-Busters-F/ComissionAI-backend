package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.execucao.*;
import com.bugbusters.backend.service.ExecucaoCalculoService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/execucoes/calculo")
public class ExecucaoCalculoController {
    private final ExecucaoCalculoService service;
    public ExecucaoCalculoController(ExecucaoCalculoService service){this.service=service;}
    @PostMapping
    public ResponseEntity<ExecucaoCalculoResponse> solicitar(@Valid @RequestBody SolicitarExecucaoCalculoRequest request,
            @RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey) {
        return ResponseEntity.accepted().body(service.solicitar(request,idempotencyKey));
    }
    @GetMapping("/{id}") public ResponseEntity<ExecucaoCalculoResponse> consultar(@PathVariable UUID id){return ResponseEntity.ok(service.consultar(id));}
    @PostMapping("/{id}/retomar") public ResponseEntity<ExecucaoCalculoResponse> retomar(@PathVariable UUID id){return ResponseEntity.accepted().body(service.retomar(id));}
}
