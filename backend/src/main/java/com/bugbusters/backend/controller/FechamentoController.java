package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.error.ApiErrorResponse;
import com.bugbusters.backend.dto.fechamento.FechamentoRequestDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoResponseDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoStatusDTO;
import com.bugbusters.backend.dto.relatorio.RelatorioFechamentoConsolidadoDTO;
import com.bugbusters.backend.dto.relatorio.TipoExportacaoRelatorio;
import com.bugbusters.backend.service.FechamentoService;
import com.bugbusters.backend.service.RelatorioFechamentoService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/comissoes/fechamento")
@Tag(name = "Fechamento Final de Competência", description = "Operações oficiais de liquidação contábil, apuração retroativa de versões vigentes e bloqueio de competências (BACK-11 / BACK-15)")
public class FechamentoController {

    private final FechamentoService fechamentoService;
    private final RelatorioFechamentoService relatorioService;

    public FechamentoController(FechamentoService fechamentoService, RelatorioFechamentoService relatorioService) {
        this.fechamentoService = fechamentoService;
        this.relatorioService = relatorioService;
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

    @Operation(
            summary = "Consolidar relatório de fechamento por competência (BUG-110 / BACK-15)",
            description = "Gera o relatório consolidado de fechamento com agrupamentos por canal de venda, equipe/loja/marca e colaborador."
    )
    @ApiResponse(responseCode = "200", description = "Relatório consolidado gerado com sucesso")
    @ApiResponse(responseCode = "404", description = "Fechamento não encontrado para a competência")
    @GetMapping("/{competencia}/relatorio")
    public ResponseEntity<RelatorioFechamentoConsolidadoDTO> obterRelatorioCompetencia(
            @Parameter(description = "Competência no formato YYYY-MM", example = "2026-10")
            @PathVariable String competencia) {
        RelatorioFechamentoConsolidadoDTO relatorio = relatorioService.gerarRelatorioCompetencia(competencia);
        return ResponseEntity.ok(relatorio);
    }

    @Operation(
            summary = "Exportar relatório de fechamento por competência em CSV (BUG-110 / BACK-15)",
            description = "Exporta o relatório consolidado em arquivo CSV com codificação UTF-8 com BOM e delimitador customizável."
    )
    @ApiResponse(responseCode = "200", description = "Arquivo CSV gerado com sucesso")
    @ApiResponse(responseCode = "404", description = "Fechamento não encontrado")
    @GetMapping(value = "/{competencia}/relatorio/exportar", produces = "text/csv")
    public ResponseEntity<byte[]> exportarRelatorioCompetenciaCsv(
            @Parameter(description = "Competência no formato YYYY-MM", example = "2026-10")
            @PathVariable String competencia,
            @Parameter(description = "Tipo de detalhamento: DETALHADO, CONSOLIDADO_CANAL, CONSOLIDADO_EQUIPE, CONSOLIDADO_COLABORADOR, COMPLETO")
            @RequestParam(required = false, defaultValue = "DETALHADO") TipoExportacaoRelatorio tipo,
            @Parameter(description = "Delimitador do CSV (padrão ';')")
            @RequestParam(required = false, defaultValue = ";") String delimitador) {
        RelatorioFechamentoConsolidadoDTO relatorio = relatorioService.gerarRelatorioCompetencia(competencia);
        byte[] csv = relatorioService.exportarCsv(relatorio, tipo, delimitador);

        String filename = String.format("relatorio_fechamento_%s_%s.csv", competencia, tipo.name().toLowerCase());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }

    @Operation(
            summary = "Consolidar relatório de fechamento por protocolo (BUG-110 / BACK-15)",
            description = "Gera o relatório consolidado a partir do protocolo oficial do fechamento."
    )
    @ApiResponse(responseCode = "200", description = "Relatório consolidado gerado com sucesso")
    @ApiResponse(responseCode = "404", description = "Fechamento não encontrado para o protocolo")
    @GetMapping("/protocolo/{protocolo}/relatorio")
    public ResponseEntity<RelatorioFechamentoConsolidadoDTO> obterRelatorioPorProtocolo(
            @Parameter(description = "Protocolo oficial do fechamento")
            @PathVariable UUID protocolo) {
        RelatorioFechamentoConsolidadoDTO relatorio = relatorioService.gerarRelatorioPorProtocolo(protocolo);
        return ResponseEntity.ok(relatorio);
    }

    @Operation(
            summary = "Exportar relatório de fechamento por protocolo em CSV (BUG-110 / BACK-15)",
            description = "Exporta o relatório consolidado em arquivo CSV a partir do protocolo oficial."
    )
    @ApiResponse(responseCode = "200", description = "Arquivo CSV gerado com sucesso")
    @ApiResponse(responseCode = "404", description = "Fechamento não encontrado")
    @GetMapping(value = "/protocolo/{protocolo}/relatorio/exportar", produces = "text/csv")
    public ResponseEntity<byte[]> exportarRelatorioProtocoloCsv(
            @Parameter(description = "Protocolo oficial do fechamento")
            @PathVariable UUID protocolo,
            @Parameter(description = "Tipo de detalhamento")
            @RequestParam(required = false, defaultValue = "DETALHADO") TipoExportacaoRelatorio tipo,
            @Parameter(description = "Delimitador do CSV (padrão ';')")
            @RequestParam(required = false, defaultValue = ";") String delimitador) {
        RelatorioFechamentoConsolidadoDTO relatorio = relatorioService.gerarRelatorioPorProtocolo(protocolo);
        byte[] csv = relatorioService.exportarCsv(relatorio, tipo, delimitador);

        String filename = String.format("relatorio_fechamento_protocolo_%s.csv", protocolo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }
}
