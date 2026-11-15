package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.pendencia.*;
import com.bugbusters.backend.service.CampanhaPendenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para aceite rastreável de pendências e governança de riscos (BUG-102 / BACK-7).
 */
@RestController
@RequestMapping("/api/v1/campanhas/{id}/pendencias")
@Tag(name = "Pendências e Riscos", description = "Endpoints de governança para aceite rastreável e auditoria de pendências")
public class CampanhaPendenciaController {

    private final CampanhaPendenciaService pendenciaService;

    public CampanhaPendenciaController(CampanhaPendenciaService pendenciaService) {
        this.pendenciaService = pendenciaService;
    }

    @PostMapping("/aceite")
    @Operation(
            summary = "Registrar aceite formal e rastreável de pendência (BUG-102 / BACK-7)",
            description = "Formaliza o aceite de uma pendência cadastral ou inconsistência com justificativa obrigatória, desbloqueando a esteira."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aceite registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou versão imutável"),
            @ApiResponse(responseCode = "404", description = "Campanha ou regra não encontrada")
    })
    public ResponseEntity<PendenciaAceiteResponse> aceitarPendencia(
            @PathVariable @Parameter(description = "ID da campanha") Long id,
            @RequestParam(required = false) @Parameter(description = "Número opcional da versão sob edição") Integer versaoNumero,
            @Valid @RequestBody AceitarPendenciaRequest request
    ) {
        PendenciaAceiteResponse response = pendenciaService.aceitarPendencia(id, versaoNumero, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{aceiteId}/revogar")
    @Operation(
            summary = "Revogar formalmente o aceite de uma pendência (BUG-102 / BACK-7)",
            description = "Revoga um aceite prévio de pendência com justificativa formal, bloqueando novamente a regra caso seja impeditiva."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aceite revogado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Aceite já revogado ou versão imutável"),
            @ApiResponse(responseCode = "404", description = "Aceite ou campanha não encontrados")
    })
    public ResponseEntity<PendenciaAceiteResponse> revogarAceite(
            @PathVariable @Parameter(description = "ID da campanha") Long id,
            @PathVariable @Parameter(description = "ID do aceite a ser revogado") Long aceiteId,
            @Valid @RequestBody RevogarAceitePendenciaRequest request
    ) {
        PendenciaAceiteResponse response = pendenciaService.revogarAceite(id, aceiteId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(
            summary = "Listar pendências e situação de aceite da campanha (BUG-102 / BACK-7)",
            description = "Retorna a relação de inconsistências e avisos com o status de aceite formal de cada uma e prontidão para a esteira."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relação consolidada de pendências"),
            @ApiResponse(responseCode = "404", description = "Campanha não encontrada")
    })
    public ResponseEntity<ResumoPendenciasCampanhaResponse> listarPendencias(
            @PathVariable @Parameter(description = "ID da campanha") Long id,
            @RequestParam(required = false) @Parameter(description = "Número opcional da versão a consultar") Integer versaoNumero
    ) {
        ResumoPendenciasCampanhaResponse resumo = pendenciaService.listarPendencias(id, versaoNumero);
        return ResponseEntity.ok(resumo);
    }

    @GetMapping("/historico")
    @Operation(
            summary = "Consultar trilha de auditoria dos aceites de pendências (BUG-102 / BACK-7)",
            description = "Retorna o histórico cronológico de todos os aceites e revogações concedidos na campanha."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico de auditoria de aceites"),
            @ApiResponse(responseCode = "404", description = "Campanha não encontrada")
    })
    public ResponseEntity<List<PendenciaAceiteResponse>> listarHistorico(
            @PathVariable @Parameter(description = "ID da campanha") Long id
    ) {
        List<PendenciaAceiteResponse> historico = pendenciaService.listarHistorico(id);
        return ResponseEntity.ok(historico);
    }
}

