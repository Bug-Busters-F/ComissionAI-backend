package com.bugbusters.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.referencia.CompetenciaReferenciaDTO;
import com.bugbusters.backend.dto.referencia.RecortePublicoRequest;
import com.bugbusters.backend.dto.referencia.RecortePublicoResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosResponse;
import com.bugbusters.backend.service.ReferenciaPublicoService;
import com.bugbusters.backend.service.SimulacaoCenariosService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/referencias")
@Tag(name = "Seleção de Referência e Recorte de Públicos", description = "Disponibilização de competências históricas, recorte reutilizável da união de públicos e simulação de cenários")
public class ReferenciaController {

    private final ReferenciaPublicoService service;
    private final SimulacaoCenariosService simulacaoService;

    @org.springframework.beans.factory.annotation.Autowired
    public ReferenciaController(ReferenciaPublicoService service, SimulacaoCenariosService simulacaoService) {
        this.service = service;
        this.simulacaoService = simulacaoService;
    }

    public ReferenciaController(ReferenciaPublicoService service) {
        this(service, null);
    }

    @Operation(
            summary = "Listar competências de referência disponíveis com métricas de cobertura",
            description = """
                    Lista todas as competências (mês/ano) disponíveis na base com dados agregados de vendas
                    (quantidade e volume financeiro) e diagnóstico de cobertura cadastral de RH e taxas base (tb_basecomiss).
                    Permite ao gestor escolher conscientemente qual competência utilizar como referência para simulações.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Lista de competências recuperada com sucesso")
    @GetMapping("/competencias")
    public ResponseEntity<List<CompetenciaReferenciaDTO>> listarCompetencias() {
        return ResponseEntity.ok(service.listarCompetenciasReferencia());
    }

    @Operation(
            summary = "Consultar detalhe e cobertura de uma competência específica",
            description = "Recupera os indicadores de cobertura cadastral e status de referência para uma competência específica (ex: '2026-09')."
    )
    @ApiResponse(responseCode = "200", description = "Competência detalhada com sucesso")
    @ApiResponse(responseCode = "400", description = "Formato de competência inválido", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/competencias/{competencia}")
    public ResponseEntity<CompetenciaReferenciaDTO> detalharCompetencia(
            @Parameter(description = "Competência no formato YYYY-MM", example = "2026-09")
            @PathVariable String competencia) {
        return ResponseEntity.ok(service.detalharCompetencia(competencia));
    }

    @Operation(
            summary = "Recortar união dos públicos sem duplicação de vendas (POST)",
            description = """
                    Executa a consulta reutilizável de recorte das vendas da competência pertencentes ao público da campanha:
                    - Filtros de cada regra combinados por E (marca, loja, cargo, canal e matrícula).
                    - Regras combinadas por OU (união dos públicos).
                    - Deduplicação obrigatória por ID da venda (cada venda é computada apenas uma vez).
                    - Recorte por público ANTES das faixas de valor (preserva vendas e classifica 'atendeFaixaValor').
                    - Validações de vínculos de RH, cargo, loja, marca, vigência de admissão/demissão, valor > 0 e presença de taxas base.
                    - Diagnóstico de sobreposição e detecção de empate de especificidade (risco de bloqueio por ambiguidade).
                    Aceita 'campanhaId' para carregar campanha existente OU lista avulsa de 'regras' para propostas em elaboração.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Recorte de público executado com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos ou regras ausentes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/recortar-publico")
    public ResponseEntity<RecortePublicoResponse> recortarPublicoPost(
            @Valid @RequestBody RecortePublicoRequest request) {
        return ResponseEntity.ok(service.recortarUniaoPublicos(request));
    }

    @Operation(
            summary = "Recortar união dos públicos sem duplicação de vendas (GET)",
            description = "Atalho para o recorte da união de públicos recebendo os parâmetros via Query String (ex: ?competencia=2026-09&campanhaId=1)."
    )
    @ApiResponse(responseCode = "200", description = "Recorte de público executado com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/recortar-publico")
    public ResponseEntity<RecortePublicoResponse> recortarPublicoGet(
            @ModelAttribute RecortePublicoRequest request) {
        return ResponseEntity.ok(service.recortarUniaoPublicos(request));
    }

    @Operation(
            summary = "Simular cenários de impacto financeiro, sensibilidade e projeção temporal (POST)",
            description = """
                    Executa a simulação em sandbox pelo motor compartilhado de cálculo sobre as vendas recortadas da competência:
                    - Cenário Base: comissão calculada estritamente pela taxa contratual de tb_basecomiss (ignora outras campanhas ativas).
                    - Cenário Proposta (100%): aplicação hipotética das regras revisadas sem alterar datas históricas; se nenhuma regra aplicar, fallback na taxa base.
                    - Cenários de Sensibilidade (80%, 120%, 150%): recálculo por venda escalando o valor e reavaliando faixas monetárias dinamicamente.
                    - Projeção Temporal: cálculo pro-rata pelos dias inclusivos da campanha ÷ 30.
                    - Sem persistência em tb_resultado_calculo e sem lançamento de comissões reais.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Simulação de cenários executada com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos ou regras ausentes", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/simular-cenarios")
    public ResponseEntity<SimulacaoCenariosResponse> simularCenariosPost(
            @Valid @RequestBody SimulacaoCenariosRequest request) {
        if (simulacaoService == null) {
            throw new IllegalStateException("Serviço de simulação de cenários não inicializado.");
        }
        return ResponseEntity.ok(simulacaoService.simularCenarios(request));
    }

    @Operation(
            summary = "Simular cenários de impacto financeiro, sensibilidade e projeção temporal (GET)",
            description = "Atalho para simulação de cenários recebendo os parâmetros via Query String."
    )
    @ApiResponse(responseCode = "200", description = "Simulação de cenários executada com sucesso")
    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Campanha não encontrada", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/simular-cenarios")
    public ResponseEntity<SimulacaoCenariosResponse> simularCenariosGet(
            @ModelAttribute SimulacaoCenariosRequest request) {
        if (simulacaoService == null) {
            throw new IllegalStateException("Serviço de simulação de cenários não inicializado.");
        }
        return ResponseEntity.ok(simulacaoService.simularCenarios(request));
    }
}
