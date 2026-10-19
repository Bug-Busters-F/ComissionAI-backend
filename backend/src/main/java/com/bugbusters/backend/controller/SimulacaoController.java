package com.bugbusters.backend.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.simulacao.*;
import com.bugbusters.backend.service.SimulacaoCenariosService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/simulacoes")
@Tag(name = "Sandbox e Persistência de Simulações Financeiras", description = "Execução, persistência isolada, consultas paginadas e memória de cálculo explicativa sem chamadas à IA (BACK-6 / BACK-7)")
public class SimulacaoController {

    private final SimulacaoCenariosService service;

    public SimulacaoController(SimulacaoCenariosService service) {
        this.service = service;
    }

    @Operation(
            summary = "Executar e persistir simulação de cenários (POST)",
            description = """
                    Executa a simulação pelo motor compartilhado de cálculo e persiste em tabelas isoladas (tb_simulacao e tb_simulacao_venda):
                    - Cenário Base: comissão calculada estritamente pela taxa contratual de tb_basecomiss (ignora outras campanhas ativas).
                    - Cenário Proposta (100%): aplicação hipotética das regras revisadas sem alterar datas históricas; fallback na base se nenhuma aplicar.
                    - Cenários de Sensibilidade (80%, 120%, 150%): recálculo por venda escalando o valor e reavaliando faixas monetárias dinamicamente.
                    - Projeção Temporal: cálculo pro-rata pelos dias inclusivos da campanha ÷ 30.
                    - Rastreabilidade: gera e grava memórias explicativas determinísticas para o consolidado e para cada venda individual.
                    - Isolamento estrito: zero impacto em tb_resultado_calculo, tb_log_calculo_imutavel e tb_sales.
                    """
    )
    @ApiResponse(responseCode = "201", description = "Simulação executada e persistida com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos ou regras ausentes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping
    public ResponseEntity<SimulacaoCenariosResponse> executarEPersistir(
            @Valid @RequestBody SimulacaoCenariosRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.simularEPersistir(request));
    }

    @Operation(
            summary = "Executar simulação de cenários com persistência (POST /cenarios)",
            description = "Atalho de execução e persistência de simulação preservando retrocompatibilidade com a API de cenários."
    )
    @ApiResponse(responseCode = "200", description = "Simulação executada e persistida com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/cenarios")
    public ResponseEntity<SimulacaoCenariosResponse> simularCenariosPost(
            @Valid @RequestBody SimulacaoCenariosRequest request) {
        return ResponseEntity.ok(service.simularEPersistir(request));
    }

    @Operation(
            summary = "Executar simulação em sandbox rápido via Query String (GET /cenarios)",
            description = "Executa a simulação em memória pelos parâmetros da Query String sem persistir em banco."
    )
    @ApiResponse(responseCode = "200", description = "Simulação executada em sandbox com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/cenarios")
    public ResponseEntity<SimulacaoCenariosResponse> simularCenariosGet(
            @ModelAttribute SimulacaoCenariosRequest request) {
        return ResponseEntity.ok(service.simularCenarios(request));
    }

    @Operation(
            summary = "Listar histórico de simulações com paginação e filtros",
            description = "Consulta a listagem de simulações registradas no sistema, com suporte a filtros opcionais por competência e campanha."
    )
    @ApiResponse(responseCode = "200", description = "Página de resumos de simulação retornada com sucesso")
    @GetMapping
    public ResponseEntity<Page<SimulacaoResumoDTO>> listarSimulacoes(
            @Parameter(description = "Competência histórica de referência (YYYY-MM)", example = "2026-09")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Identificador da campanha vinculada", example = "1")
            @RequestParam(required = false) Long campanhaId,

            @PageableDefault(size = 20, sort = "executadoEm", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listarSimulacoes(competencia, campanhaId, pageable));
    }

    @Operation(
            summary = "Consultar detalhe e totais de uma simulação por ID",
            description = "Recupera o cabeçalho, parâmetros, totais consolidados e memória explicativa global da simulação especificada."
    )
    @ApiResponse(responseCode = "200", description = "Simulação encontrada com sucesso")
    @ApiResponse(responseCode = "404", description = "Simulação não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<SimulacaoCenariosResponse> buscarPorId(
            @Parameter(description = "Identificador único da simulação (UUID)")
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(service.buscarSimulacaoPorId(id));
    }

    @Operation(
            summary = "Consultar vendas paginadas com totais globais independentes da página",
            description = """
                    Retorna as vendas detalhadas da simulação com paginação, filtros e memória individual de cálculo.
                    Os totais consolidados dos cenários 100%, 80%, 120% e 150% contidos no cabeçalho da resposta
                    são independentes da página consultada, refletindo o universo total simulado.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Vendas paginadas recuperadas com sucesso")
    @ApiResponse(responseCode = "404", description = "Simulação não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/{id}/vendas")
    public ResponseEntity<SimulacaoVendasPaginadasDTO> listarVendasPaginadas(
            @Parameter(description = "Identificador único da simulação (UUID)")
            @PathVariable UUID id,

            @Parameter(description = "Filtrar apenas vendas cujo valor de comissão sofreu alteração em relação à base")
            @RequestParam(required = false) Boolean apenasComAlteracao,

            @Parameter(description = "Filtrar apenas vendas que mudaram de faixa em cenários de sensibilidade (80%, 120%, 150%)")
            @RequestParam(required = false) Boolean apenasComMudancaFaixa,

            @Parameter(description = "Filtrar apenas vendas com impedimentos cadastrais")
            @RequestParam(required = false) Boolean apenasComImpedimento,

            @PageableDefault(size = 50, sort = "dataVenda", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listarVendasPaginadas(id, apenasComAlteracao, apenasComMudancaFaixa, apenasComImpedimento, pageable));
    }

    @Operation(
            summary = "Consultar detalhes e memória de cálculo de uma venda individual da simulação",
            description = "Recupera os 4 cenários, faixas monetárias, bônus e a narrativa explicativa determinística de cálculo de uma venda específica sem chamadas à IA."
    )
    @ApiResponse(responseCode = "200", description = "Detalhes da venda simulada recuperados com sucesso")
    @ApiResponse(responseCode = "404", description = "Venda ou simulação não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/{id}/vendas/{idVenda}")
    public ResponseEntity<VendaSimuladaDetalheDTO> buscarVendaSimuladaDetalhe(
            @Parameter(description = "Identificador único da simulação (UUID)")
            @PathVariable UUID id,

            @Parameter(description = "Identificador único da venda (UUID)")
            @PathVariable UUID idVenda
    ) {
        return ResponseEntity.ok(service.buscarVendaSimuladaDetalhe(id, idVenda));
    }

    @Operation(
            summary = "Registrar aceite formal de uma simulação (BACK-8)",
            description = """
                    Registra o aceite de uma simulação que foi concluída sem impedimentos.
                    Vincula o aceite à campanha correspondente e congela a assinatura canônica (fingerprint) da proposta.
                    Bloqueia o aceite caso existam impedimentos cadastrais ou inconsistências de taxa base.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Aceite registrado com sucesso e proposta homologada")
    @ApiResponse(responseCode = "400", description = "Simulação possui impedimentos cadastrais ou não vinculada à campanha", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Simulação não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/{id}/aceite")
    public ResponseEntity<AceiteSimulacaoResponse> aceitarSimulacao(
            @Parameter(description = "Identificador único da simulação (UUID)")
            @PathVariable UUID id,
            @Valid @RequestBody AceiteSimulacaoRequest request
    ) {
        return ResponseEntity.ok(service.aceitarSimulacao(id, request));
    }
}
