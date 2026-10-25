package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.fechamento.FechamentoRequestDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoResponseDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoStatusDTO;
import com.bugbusters.backend.service.FechamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comissoes/fechamento")
@Tag(name = "Fechamento Final de Competência", description = "Operações oficiais de liquidação contábil, apuração retroativa de versões vigentes e bloqueio de competências (BACK-11)")
public class FechamentoController {

    private final FechamentoService fechamentoService;

    public FechamentoController(FechamentoService fechamentoService) {
        this.fechamentoService = fechamentoService;
    }

    @Operation(
            summary = "Executar fechamento final de competência mensal (BACK-11)",
            description = """
                    Executa a apuração definitiva de comissões para toda a competência mensal informada (ex: '2026-10').
                    Aplica a versão aprovada mais recente (versaoVigente) para todas as vendas da competência,
                    inclusive vendas ocorridas antes da edição das regras.
                    Registra o número da versão vigente em cada resultado de cálculo e snapshot de auditoria,
                    armazena o fechamento oficial com protocolo em tb_fechamento_competencia e lacra a competência
                    contra novas alterações e publicações de regras.
                    Em caso de falha, a competência é liberada e registrada com status FALHOU, nunca CONCLUIDO.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Fechamento executado e consolidado com sucesso")
    @ApiResponse(responseCode = "400", description = "Competência inválida, já fechada ou com fechamento em andamento",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping
    public ResponseEntity<FechamentoResponseDTO> executarFechamento(
            @Valid @RequestBody FechamentoRequestDTO request) {
        FechamentoResponseDTO response = fechamentoService.executarFechamento(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Consultar status de fechamento e bloqueio de uma competência (BACK-11)",
            description = "Informa se a competência está ABERTA, PROCESSANDO, CONCLUIDO ou FALHOU, detalhando os protocolos e metadados de fechamento."
    )
    @ApiResponse(responseCode = "200", description = "Status da competência retornado com sucesso")
    @ApiResponse(responseCode = "400", description = "Competência inválida",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/{competencia}/status")
    public ResponseEntity<FechamentoStatusDTO> consultarStatusCompetencia(
            @Parameter(description = "Competência no formato YYYY-MM", example = "2026-10")
            @PathVariable String competencia) {
        FechamentoStatusDTO status = fechamentoService.consultarStatusCompetencia(competencia);
        return ResponseEntity.ok(status);
    }

    @Operation(
            summary = "Listar histórico de fechamentos finais",
            description = "Recupera listagem paginada dos fechamentos de competências executados, com suporte a filtro opcional por competência."
    )
    @ApiResponse(responseCode = "200", description = "Página de fechamentos retornada com sucesso")
    @GetMapping
    public ResponseEntity<Page<FechamentoResponseDTO>> listarFechamentos(
            @Parameter(description = "Filtro opcional por competência (YYYY-MM)")
            @RequestParam(required = false) String competencia,
            @PageableDefault(size = 20, sort = "iniciadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FechamentoResponseDTO> page = fechamentoService.listarFechamentos(pageable, competencia);
        return ResponseEntity.ok(page);
    }
}
