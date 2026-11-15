package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.AtualizarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.ProgramaPythonResponse;
import com.bugbusters.backend.dto.programapython.RejeitarProgramaPythonRequest;
import com.bugbusters.backend.service.CampanhaProgramaPythonService;
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
@Tag(name = "Programa Python da Campanha (XAI / Executável)", description = "Gerenciamento, versionamento e aprovação técnica formal do código Python da campanha (BUG-104 / BACK-9)")
public class CampanhaProgramaPythonController {

    private final CampanhaProgramaPythonService programaPythonService;

    public CampanhaProgramaPythonController(CampanhaProgramaPythonService programaPythonService) {
        this.programaPythonService = programaPythonService;
    }

    @Operation(summary = "Obter o programa Python executável atual da campanha",
            description = "Retorna o código Python completo versionado, seu hash SHA-256 e status de homologação da versão ativa/proposta. Gera automaticamente se não existir.")
    @ApiResponse(responseCode = "200", description = "Programa Python retornado com sucesso")
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/programa-python")
    public ResponseEntity<ProgramaPythonResponse> obterProgramaAtual(@PathVariable Long id) {
        return ResponseEntity.ok(programaPythonService.obterOuGerarProgramaAtual(id, null));
    }

    @Operation(summary = "Obter o programa Python de uma versão específica da campanha")
    @ApiResponse(responseCode = "200", description = "Programa Python da versão retornado com sucesso")
    @ApiResponse(responseCode = "404", description = "Campanha ou versão não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/versoes/{versao}/programa-python")
    public ResponseEntity<ProgramaPythonResponse> obterProgramaPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao
    ) {
        return ResponseEntity.ok(programaPythonService.obterOuGerarProgramaAtual(id, versao));
    }

    @Operation(summary = "Atualizar código-fonte do programa Python da campanha",
            description = "Submete novo código executável, gerando nova versão do programa, recalculando hash SHA-256 e invalidando aprovações prévias.")
    @ApiResponse(responseCode = "200", description = "Código atualizado e nova versão gerada")
    @ApiResponse(responseCode = "400", description = "Código inválido ou versão já publicada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PutMapping("/programa-python")
    public ResponseEntity<ProgramaPythonResponse> atualizarCodigo(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarProgramaPythonRequest request
    ) {
        return ResponseEntity.ok(programaPythonService.salvarOuAtualizarCodigo(id, null, request));
    }

    @Operation(summary = "Atualizar código-fonte de uma versão específica da campanha")
    @PutMapping("/versoes/{versao}/programa-python")
    public ResponseEntity<ProgramaPythonResponse> atualizarCodigoPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao,
            @Valid @RequestBody AtualizarProgramaPythonRequest request
    ) {
        return ResponseEntity.ok(programaPythonService.salvarOuAtualizarCodigo(id, versao, request));
    }

    @Operation(summary = "Aprovar formalmente o programa Python na revisão técnica (Etapa 2)",
            description = "Homologa o código executável, valida hash SHA-256 e avança a esteira para a etapa de simulação.")
    @ApiResponse(responseCode = "200", description = "Programa aprovado com sucesso")
    @ApiResponse(responseCode = "400", description = "Divergência de hash ou erro de negócio", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/programa-python/aprovar")
    public ResponseEntity<ProgramaPythonResponse> aprovarPrograma(
            @PathVariable Long id,
            @Valid @RequestBody AprovarProgramaPythonRequest request
    ) {
        return ResponseEntity.ok(programaPythonService.aprovarPrograma(id, null, request));
    }

    @Operation(summary = "Aprovar programa Python de uma versão específica")
    @PostMapping("/versoes/{versao}/programa-python/aprovar")
    public ResponseEntity<ProgramaPythonResponse> aprovarProgramaPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao,
            @Valid @RequestBody AprovarProgramaPythonRequest request
    ) {
        return ResponseEntity.ok(programaPythonService.aprovarPrograma(id, versao, request));
    }

    @Operation(summary = "Rejeitar programa Python na revisão técnica",
            description = "Registra a recusa técnica com justificativa para devolução à etapa anterior.")
    @ApiResponse(responseCode = "200", description = "Programa rejeitado com sucesso")
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/programa-python/rejeitar")
    public ResponseEntity<ProgramaPythonResponse> rejeitarPrograma(
            @PathVariable Long id,
            @Valid @RequestBody RejeitarProgramaPythonRequest request
    ) {
        return ResponseEntity.ok(programaPythonService.rejeitarPrograma(id, null, request));
    }

    @Operation(summary = "Rejeitar programa Python de uma versão específica")
    @PostMapping("/versoes/{versao}/programa-python/rejeitar")
    public ResponseEntity<ProgramaPythonResponse> rejeitarProgramaPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao,
            @Valid @RequestBody RejeitarProgramaPythonRequest request
    ) {
        return ResponseEntity.ok(programaPythonService.rejeitarPrograma(id, versao, request));
    }

    @Operation(summary = "Consultar histórico de versões do programa Python da campanha",
            description = "Lista todas as revisões do código executável e os carimbos de auditoria de cada uma.")
    @ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso")
    @GetMapping("/programa-python/historico")
    public ResponseEntity<List<ProgramaPythonResponse>> listarHistorico(@PathVariable Long id) {
        return ResponseEntity.ok(programaPythonService.listarHistorico(id, null));
    }

    @Operation(summary = "Consultar histórico de versões do programa Python de uma versão específica")
    @GetMapping("/versoes/{versao}/programa-python/historico")
    public ResponseEntity<List<ProgramaPythonResponse>> listarHistoricoPorVersao(
            @PathVariable Long id,
            @PathVariable Integer versao
    ) {
        return ResponseEntity.ok(programaPythonService.listarHistorico(id, versao));
    }
}

