package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraRequest;
import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraResponse;
import com.bugbusters.backend.service.InterpretadorService;
import com.bugbusters.backend.dto.interpretador.proposta.AtualizarArtefatosRequest;
import com.bugbusters.backend.dto.interpretador.proposta.AtualizarArtefatosResponse;
import com.bugbusters.backend.dto.interpretador.proposta.InterpretacaoMultiplaRequest;
import com.bugbusters.backend.dto.interpretador.proposta.InterpretacaoMultiplaResponse;
import com.bugbusters.backend.dto.interpretador.proposta.ReinterpretarBlocoRequest;
import com.bugbusters.backend.dto.interpretador.proposta.ReinterpretarBlocoResponse;
import com.bugbusters.backend.dto.interpretador.CatalogoDominioResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Operation(summary = "Extrair múltiplas propostas de regras com faixas e ajustes sobre a base",
               description = "Interpreta texto livre com uma ou múltiplas regras, limites inclusivos/exclusivos, operações sobre a taxa base e gera artefatos explicativos (XAI e Python equivalente).")
    @ApiResponse(responseCode = "200", description = "Lista de propostas interpretadas e estruturadas com sucesso")
    @ApiResponse(responseCode = "400", description = "Comando vazio ou requisição inválida", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Serviço de IA Python indisponível", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "504", description = "Tempo limite excedido na inferência", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/extrair-propostas")
    public ResponseEntity<InterpretacaoMultiplaResponse> extrairPropostas(@Valid @RequestBody InterpretacaoMultiplaRequest request) {
        return ResponseEntity.ok(interpretadorService.processarInterpretacaoMultipla(request));
    }

    @Operation(summary = "Reinterpretação localizada de bloco de proposta",
               description = "Ajusta ou reinterpreta um bloco específico dentro da proposta a partir de nova instrução do usuário, mantendo os demais blocos e o contexto inalterados.")
    @ApiResponse(responseCode = "200", description = "Bloco reprocessado e lista consolidada devolvida")
    @ApiResponse(responseCode = "400", description = "Dados da reinterpretação inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Bloco especificado não localizado", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/reinterpretar-bloco")
    public ResponseEntity<ReinterpretarBlocoResponse> reinterpretarBloco(@Valid @RequestBody ReinterpretarBlocoRequest request) {
        return ResponseEntity.ok(interpretadorService.reinterpretarBloco(request));
    }

    @Operation(summary = "Atualizar artefatos explicativos e sincronizar taxas",
               description = "Recalcula deterministicamente a taxa efetiva, valida pendências e regera a explicação XAI e o código Python equivalente após edição manual de campos no frontend.")
    @ApiResponse(responseCode = "200", description = "Artefatos recalculados e sincronizados com sucesso")
    @ApiResponse(responseCode = "400", description = "Campos da proposta com inconsistências impeditivas", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/atualizar-artefatos")
    public ResponseEntity<AtualizarArtefatosResponse> atualizarArtefatos(@Valid @RequestBody AtualizarArtefatosRequest request) {
        return ResponseEntity.ok(interpretadorService.atualizarArtefatos(request));
    }

    @Operation(summary = "Consultar catálogo de informações de domínio conhecidas do cliente",
               description = "Disponibiliza marcas, cargos com canais associados, 80 lojas, faixas de matrícula e tabela de 30 taxas contratuais padrão da base do cliente.")
    @ApiResponse(responseCode = "200", description = "Catálogo de domínio recuperado com sucesso")
    @GetMapping("/catalogo")
    public ResponseEntity<CatalogoDominioResponseDTO> obterCatalogoDominio() {
        return ResponseEntity.ok(CatalogoDominioResponseDTO.construir());
    }
}