package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.cobertura.AnexarBaseCampanhaRequestDTO;
import com.bugbusters.backend.dto.cobertura.AnexoBaseCampanhaResponseDTO;
import com.bugbusters.backend.dto.cobertura.CoberturaCampanhaResponseDTO;
import com.bugbusters.backend.dto.cobertura.PendenciaBaseCoberturaDTO;
import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.service.CoberturaFechamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campanhas/{campanhaId}")
@Tag(name = "Cobertura de Fechamento por Campanha", description = "Validação de cobertura de dados, diagnóstico de competências e anexos de base para o fechamento")
public class CampanhaCoberturaController {

    private final CoberturaFechamentoService coberturaFechamentoService;

    public CampanhaCoberturaController(CoberturaFechamentoService coberturaFechamentoService) {
        this.coberturaFechamentoService = coberturaFechamentoService;
    }

    @Operation(summary = "Diagnosticar cobertura de fechamento da campanha",
            description = "Avalia todas as competências que intersectam a vigência da versão vigente da campanha, verificando lotes anexados, RH, Vendas, Taxas e pendências.")
    @ApiResponse(responseCode = "200", description = "Diagnóstico executado com sucesso")
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/cobertura-fechamento")
    public ResponseEntity<CoberturaCampanhaResponseDTO> diagnosticarCobertura(
            @Parameter(description = "Identificador da campanha", example = "15")
            @PathVariable Long campanhaId) {
        return ResponseEntity.ok(coberturaFechamentoService.diagnosticarCobertura(campanhaId));
    }

    @Operation(summary = "Validar prontidão de fechamento da campanha",
            description = "Executa a validação bloqueante com retenção transacional de travas. Retorna 200 se a campanha estiver apta ao fechamento ou 422 se houver pendências impeditivas.")
    @ApiResponse(responseCode = "200", description = "Campanha 100% coberta e pronta para fechamento")
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Cobertura incompleta ou pendências impeditivas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/cobertura-fechamento/validar")
    @Transactional
    public ResponseEntity<CoberturaCampanhaResponseDTO> validarParaFechamento(
            @Parameter(description = "Identificador da campanha", example = "15")
            @PathVariable Long campanhaId) {
        return ResponseEntity.ok(coberturaFechamentoService.validarParaFechamento(campanhaId));
    }

    @Operation(summary = "Listar pendências de importação da campanha",
            description = "Consulta paginada das inconsistências geradas nos lotes anexados à campanha, com filtros opcionais por competência e tipo de base.")
    @ApiResponse(responseCode = "200", description = "Lista paginada de pendências")
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/cobertura-fechamento/pendencias")
    public ResponseEntity<Page<PendenciaBaseCoberturaDTO>> listarPendencias(
            @Parameter(description = "Identificador da campanha", example = "15")
            @PathVariable Long campanhaId,
            @Parameter(description = "Filtro por competência (YYYY-MM)", example = "2026-07")
            @RequestParam(required = false) String competencia,
            @Parameter(description = "Filtro por tipo de base (HR, SALES, COMISSIONS)", example = "SALES")
            @RequestParam(required = false) String tipoBase,
            @Parameter(description = "Número da página", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(coberturaFechamentoService.listarPendencias(campanhaId, competencia, tipoBase, pageable));
    }

    @Operation(summary = "Listar anexos ativos da campanha",
            description = "Retorna todos os lotes gerais atualmente vinculados como anexo da campanha.")
    @ApiResponse(responseCode = "200", description = "Lista de anexos ativos")
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/anexos-base")
    public ResponseEntity<List<AnexoBaseCampanhaResponseDTO>> listarAnexos(
            @Parameter(description = "Identificador da campanha", example = "15")
            @PathVariable Long campanhaId) {
        return ResponseEntity.ok(coberturaFechamentoService.listarAnexosAtivos(campanhaId));
    }

    @Operation(summary = "Anexar lote de base à campanha",
            description = "Vincula um lote concluído (identificado por reportId) à campanha, protegendo-o contra exclusão acidental.")
    @ApiResponse(responseCode = "201", description = "Lote anexado com sucesso")
    @ApiResponse(responseCode = "400", description = "Lote inválido, excluído ou fora da vigência", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha ou lote não encontrado", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Lote já está anexado à campanha", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/anexos-base")
    public ResponseEntity<AnexoBaseCampanhaResponseDTO> anexarBase(
            @Parameter(description = "Identificador da campanha", example = "15")
            @PathVariable Long campanhaId,
            @Valid @RequestBody AnexarBaseCampanhaRequestDTO request) {
        AnexoBaseCampanhaResponseDTO anexo = coberturaFechamentoService.anexarBase(campanhaId, request);
        URI uri = URI.create("/api/v1/campanhas/" + campanhaId + "/anexos-base/" + anexo.id());
        return ResponseEntity.created(uri).body(anexo);
    }

    @Operation(summary = "Desanexar lote da campanha",
            description = "Desvincula logicamente o lote da campanha (bloqueado se a competência estiver fechada ou em fechamento).")
    @ApiResponse(responseCode = "204", description = "Lote desanexado com sucesso")
    @ApiResponse(responseCode = "400", description = "Competência bloqueada por fechamento", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha ou anexo não encontrado", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/anexos-base/{anexoId}")
    public ResponseEntity<Void> desanexarBase(
            @Parameter(description = "Identificador da campanha", example = "15")
            @PathVariable Long campanhaId,
            @Parameter(description = "Identificador do anexo", example = "9f8e7d6c-5b4a-3210-fedc-ba0987654321")
            @PathVariable UUID anexoId,
            @Parameter(description = "Responsável pela desassociação (paliativo provisório até autenticação BACK-1)", example = "gestor@empresa.com")
            @RequestParam(required = false) String responsavel) {
        coberturaFechamentoService.desanexarBase(campanhaId, anexoId, responsavel);
        return ResponseEntity.noContent().build();
    }
}
