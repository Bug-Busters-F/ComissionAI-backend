package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.campanha.AlterarEstadoCampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaResponse;
import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.service.CampanhaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/campanhas")
@Tag(name = "1. Campanhas e Regras", description = "Gerenciamento do ciclo de vida de campanhas e propostas de regras")
public class CampanhaController {

    private final CampanhaService campanhaService;

    public CampanhaController(CampanhaService campanhaService) {
        this.campanhaService = campanhaService;
    }

    @Operation(summary = "Cadastrar campanha com regra vinculada", description = "Salva a campanha como rascunho (DRAFT). Caso a data final seja omitida, atribui 30 dias contados da data atual.")
    @ApiResponse(responseCode = "201", description = "Campanha criada com sucesso")
    @ApiResponse(responseCode = "400", description = "Dados inválidos ou período incoerente", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping
    public ResponseEntity<CampanhaResponse> criarCampanha(@Valid @RequestBody CampanhaRequest request) {
        CampanhaResponse response = campanhaService.criarCampanha(request);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @Operation(summary = "Listar campanhas", description = "Lista todas as campanhas cadastradas não removidas, com filtro opcional por estado (DRAFT, ATIVA, INATIVA, CONCLUIDA, CANCELADA).")
    @GetMapping
    public ResponseEntity<List<CampanhaResponse>> listarCampanhas(
            @RequestParam(required = false) EstadoCampanha estado) {
        return ResponseEntity.ok(campanhaService.listar(estado));
    }

    @Operation(summary = "Alterar estado da campanha", description = "Transiciona o estado da campanha para qualquer um dos valores: DRAFT, ATIVA, INATIVA, CONCLUIDA, CANCELADA e sincroniza a regra vinculada.")
    @ApiResponse(responseCode = "200", description = "Estado alterado com sucesso")
    @ApiResponse(responseCode = "400", description = "Estado inválido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada ou excluída", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PatchMapping("/{id}/estado")
    public ResponseEntity<CampanhaResponse> alterarEstado(
            @PathVariable Long id,
            @Valid @RequestBody AlterarEstadoCampanhaRequest request) {
        return ResponseEntity.ok(campanhaService.alterarEstado(id, request.estado()));
    }

    @Operation(summary = "Consultar detalhe da campanha por ID")
    @ApiResponse(responseCode = "200", description = "Campanha localizada")
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada ou excluída", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<CampanhaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(campanhaService.buscarPorId(id));
    }

    @Operation(summary = "Atualizar campanha e regra vinculada")
    @PutMapping("/{id}")
    public ResponseEntity<CampanhaResponse> atualizarCampanha(@PathVariable Long id, @Valid @RequestBody CampanhaRequest request) {
        return ResponseEntity.ok(campanhaService.atualizarCampanha(id, request));
    }

    @Operation(summary = "Remoção lógica da campanha", description = "Aplica soft delete preservando referências históricas para cálculos.")
    @ApiResponse(responseCode = "204", description = "Campanha desativada com sucesso")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removerCampanha(@PathVariable Long id) {
        campanhaService.removerLogicamente(id);
        return ResponseEntity.noContent().build();
    }
}