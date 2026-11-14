package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.alerta.*;
import com.bugbusters.backend.model.TipoAlertaEstatistico;
import com.bugbusters.backend.service.AlertaEstatisticoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alertas-estatisticos")
@Tag(name = "Alertas estatísticos", description = "Anomalias persistidas após efetivação de bases")
public class AlertaEstatisticoController {
    private final AlertaEstatisticoService service;
    public AlertaEstatisticoController(AlertaEstatisticoService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Listar alertas estatísticos ativos ou históricos")
    public ResponseEntity<Page<AlertaEstatisticoResponse>> listar(
            @RequestParam(required = false) String matricula, @RequestParam(required = false) String competencia,
            @RequestParam(required = false) TipoAlertaEstatistico tipo, @RequestParam(required = false) UUID lotePreparacaoId,
            @RequestParam(defaultValue = "false") boolean incluirSubstituidos,
            @PageableDefault(size = 20, sort = "geradoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.consultar(matricula, competencia, tipo, lotePreparacaoId, incluirSubstituidos, pageable));
    }

    @PostMapping("/reprocessar")
    @Operation(summary = "Reprocessar alertas de um lote efetivado", description = "Disponível somente para administrador ou gerente; substitui resultados anteriores quando as bases mudaram.")
    public ResponseEntity<ReprocessamentoAlertasResponse> reprocessar(@Valid @RequestBody ReprocessarAlertasRequest request) {
        return ResponseEntity.ok(service.reprocessar(request.lotePreparacaoId()));
    }
}
