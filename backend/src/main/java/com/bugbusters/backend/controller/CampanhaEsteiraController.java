package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.esteira.*;
import com.bugbusters.backend.service.CampanhaEsteiraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/campanhas/{id}")
@Tag(name = "Esteira de Governança e Aprovações da Campanha", description = "Ciclo de vida, gates de aprovação, devolução com justificativa e histórico auditável (BUG-99 / BACK-4)")
public class CampanhaEsteiraController {

    private final CampanhaEsteiraService esteiraService;

    public CampanhaEsteiraController(CampanhaEsteiraService esteiraService) {
        this.esteiraService = esteiraService;
    }

    @Operation(summary = "Obter o estado consolidado da esteira da campanha",
            description = "Retorna a etapa atual, etapa mais avançada, estado formal da esteira, pendências de devolução e se está apta para avançar.")
    @ApiResponse(responseCode = "200", description = "Estado da esteira retornado com sucesso")
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/esteira")
    public ResponseEntity<EstadoEsteiraResponse> obterEstadoEsteira(@PathVariable Long id) {
        return ResponseEntity.ok(esteiraService.obterEstadoEsteira(id, null));
    }

    @Operation(summary = "Obter o estado da esteira de uma versão específica da campanha")
    @ApiResponse(responseCode = "200", description = "Estado da esteira retornado com sucesso")
    @ApiResponse(responseCode = "404", description = "Campanha ou versão não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/versoes/{versao}/esteira")
    public ResponseEntity<EstadoEsteiraResponse> obterEstadoEsteiraPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao
    ) {
        return ResponseEntity.ok(esteiraService.obterEstadoEsteira(id, versao));
    }

    @Operation(summary = "Avançar ou transicionar etapa na esteira com validação de pré-requisitos",
            description = "Valida os gates obrigatórios (regras na etapa 2, programa Python na etapa 3, simulação na etapa 4) e limpa pendências de devolução.")
    @ApiResponse(responseCode = "200", description = "Etapa avançada com sucesso")
    @ApiResponse(responseCode = "400", description = "Pré-requisito não atendido ou transição inválida", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/esteira/avancar")
    public ResponseEntity<EstadoEsteiraResponse> avancarEtapa(
            @PathVariable Long id,
            @Valid @RequestBody AvancarEsteiraRequest request
    ) {
        return ResponseEntity.ok(esteiraService.avancarEtapa(id, null, request));
    }

    @Operation(summary = "Avançar etapa de uma versão específica na esteira")
    @PostMapping("/versoes/{versao}/esteira/avancar")
    public ResponseEntity<EstadoEsteiraResponse> avancarEtapaPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao,
            @Valid @RequestBody AvancarEsteiraRequest request
    ) {
        return ResponseEntity.ok(esteiraService.avancarEtapa(id, versao, request));
    }

    @Operation(summary = "Devolver a campanha para etapa anterior com justificativa obrigatória (FRONT-9 / BACK-4)",
            description = "Permite ao gerente ou revisor devolver a proposta para a etapa 1, 2 ou 3, registrando carimbos de auditoria e recuando a esteira.")
    @ApiResponse(responseCode = "200", description = "Campanha devolvida com sucesso")
    @ApiResponse(responseCode = "400", description = "Etapa de destino inválida ou justificativa ausente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/esteira/devolver")
    public ResponseEntity<EstadoEsteiraResponse> devolverCampanha(
            @PathVariable Long id,
            @Valid @RequestBody DevolverCampanhaRequest request
    ) {
        return ResponseEntity.ok(esteiraService.devolverCampanha(id, null, request));
    }

    @Operation(summary = "Devolver versão específica da campanha na esteira")
    @PostMapping("/versoes/{versao}/esteira/devolver")
    public ResponseEntity<EstadoEsteiraResponse> devolverCampanhaPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao,
            @Valid @RequestBody DevolverCampanhaRequest request
    ) {
        return ResponseEntity.ok(esteiraService.devolverCampanha(id, versao, request));
    }

    @Operation(summary = "Aprovação gerencial formal da campanha (Etapa 4)",
            description = "Valida código Python aprovado e simulação aceita, homologa a proposta e transita para o estado APROVADA.")
    @ApiResponse(responseCode = "200", description = "Campanha aprovada pelo gerente com sucesso")
    @ApiResponse(responseCode = "400", description = "Pendências impeditivas (código ou simulação não homologados)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/esteira/aprovar")
    public ResponseEntity<EstadoEsteiraResponse> aprovarGerente(
            @PathVariable Long id,
            @Valid @RequestBody AprovarGerenteRequest request
    ) {
        return ResponseEntity.ok(esteiraService.aprovarGerente(id, null, request));
    }

    @Operation(summary = "Aprovação gerencial de uma versão específica da campanha")
    @PostMapping("/versoes/{versao}/esteira/aprovar")
    public ResponseEntity<EstadoEsteiraResponse> aprovarGerentePorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao,
            @Valid @RequestBody AprovarGerenteRequest request
    ) {
        return ResponseEntity.ok(esteiraService.aprovarGerente(id, versao, request));
    }

    @Operation(summary = "Consultar histórico de transições e movimentações na esteira",
            description = "Lista todos os avanços, retornos, aprovações e devoluções auditáveis com data, autor e justificativa.")
    @ApiResponse(responseCode = "200", description = "Histórico da esteira retornado com sucesso")
    @GetMapping("/esteira/historico")
    public ResponseEntity<List<EsteiraTransicaoResponse>> listarHistorico(@PathVariable Long id) {
        return ResponseEntity.ok(esteiraService.listarHistorico(id, null));
    }

    @Operation(summary = "Consultar histórico da esteira de uma versão específica")
    @GetMapping("/versoes/{versao}/esteira/historico")
    public ResponseEntity<List<EsteiraTransicaoResponse>> listarHistoricoPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao
    ) {
        return ResponseEntity.ok(esteiraService.listarHistorico(id, versao));
    }
}

