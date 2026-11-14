package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.apuracao.ApuracaoCampanhaResponse;
import com.bugbusters.backend.dto.apuracao.ApuracaoResumoDTO;
import com.bugbusters.backend.dto.apuracao.CalculoDefinitivoCampanhaRequest;
import com.bugbusters.backend.dto.relatorio.RelatorioFechamentoConsolidadoDTO;
import com.bugbusters.backend.dto.relatorio.TipoExportacaoRelatorio;
import com.bugbusters.backend.service.CampanhaApuracaoService;
import com.bugbusters.backend.service.RelatorioFechamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller REST para Apuração Definitiva por Campanha no Motor Atual (BUG-109 / BACK-14).
 */
@RestController
@RequestMapping("/api/v1/campanhas/{id}")
@Tag(name = "Apuração Definitiva da Campanha", description = "Endpoints para cálculo definitivo e liquidação financeira por campanha")
public class CampanhaApuracaoController {

    private final CampanhaApuracaoService apuracaoService;
    private final RelatorioFechamentoService relatorioService;

    public CampanhaApuracaoController(CampanhaApuracaoService apuracaoService, RelatorioFechamentoService relatorioService) {
        this.apuracaoService = apuracaoService;
        this.relatorioService = relatorioService;
    }

    @PostMapping("/calcular-definitivo")
    @Operation(
            summary = "Executar cálculo definitivo por campanha no motor atual (BUG-109 / BACK-14)",
            description = "Executa a liquidação e o cálculo definitivo das comissões sobre as vendas da campanha, congelando os resultados e logs de auditoria imutável."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Apuração definitiva executada e congelada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Campanha devolvida, pendências impeditivas em aberto ou sem programa Python homologado"),
            @ApiResponse(responseCode = "404", description = "Campanha não encontrada")
    })
    public ResponseEntity<ApuracaoCampanhaResponse> calcularDefinitivo(
            @PathVariable @Parameter(description = "ID da campanha") Long id,
            @RequestParam(required = false) @Parameter(description = "Número da versão a ser apurada") Integer versaoNumero,
            @Valid @RequestBody(required = false) CalculoDefinitivoCampanhaRequest request
    ) {
        ApuracaoCampanhaResponse response = apuracaoService.calcularDefinitivo(id, versaoNumero, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/apuracao-definitiva")
    @Operation(
            summary = "Consultar última apuração definitiva da campanha (BUG-109 / BACK-14)",
            description = "Retorna os resultados e totais consolidados da apuração mais recente da campanha."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Apuração recuperada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Campanha ou apuração não encontrada")
    })
    public ResponseEntity<ApuracaoCampanhaResponse> obterUltimaApuracao(
            @PathVariable @Parameter(description = "ID da campanha") Long id
    ) {
        ApuracaoCampanhaResponse response = apuracaoService.obterUltimaApuracao(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/apuracoes")
    @Operation(
            summary = "Listar histórico de apurações da campanha (BUG-109 / BACK-14)",
            description = "Retorna o histórico resumido de todas as apurações financeiras realizadas para a campanha."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico listado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Campanha não encontrada")
    })
    public ResponseEntity<List<ApuracaoResumoDTO>> listarHistoricoApuracoes(
            @PathVariable @Parameter(description = "ID da campanha") Long id
    ) {
        List<ApuracaoResumoDTO> historico = apuracaoService.listarHistoricoApuracoes(id);
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/apuracoes/{apuracaoId}")
    @Operation(
            summary = "Consultar detalhes de uma apuração específica pelo ID (BUG-109 / BACK-14)",
            description = "Retorna os detalhes completos e itens de uma apuração definitiva histórica."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Apuração detalhada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Apuração não encontrada")
    })
    public ResponseEntity<ApuracaoCampanhaResponse> obterApuracaoPorId(
            @PathVariable @Parameter(description = "ID da campanha") Long id,
            @PathVariable @Parameter(description = "ID da apuração") UUID apuracaoId
    ) {
        ApuracaoCampanhaResponse response = apuracaoService.obterApuracaoPorId(id, apuracaoId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/apuracao-definitiva/relatorio")
    @Operation(
            summary = "Consolidar relatório da apuração definitiva mais recente (BUG-110 / BACK-15)",
            description = "Gera o relatório consolidado por canal, equipe e colaborador da apuração definitiva mais recente da campanha."
    )
    public ResponseEntity<RelatorioFechamentoConsolidadoDTO> obterRelatorioUltimaApuracao(
            @PathVariable @Parameter(description = "ID da campanha") Long id
    ) {
        RelatorioFechamentoConsolidadoDTO relatorio = relatorioService.gerarRelatorioCampanha(id, null);
        return ResponseEntity.ok(relatorio);
    }

    @GetMapping(value = "/apuracao-definitiva/exportar", produces = "text/csv")
    @Operation(
            summary = "Exportar relatório CSV da apuração definitiva mais recente (BUG-110 / BACK-15)",
            description = "Exporta o relatório consolidado da apuração mais recente em CSV formatado UTF-8 com BOM."
    )
    public ResponseEntity<byte[]> exportarRelatorioUltimaApuracaoCsv(
            @PathVariable @Parameter(description = "ID da campanha") Long id,
            @Parameter(description = "Tipo de detalhamento: DETALHADO, CONSOLIDADO_CANAL, CONSOLIDADO_EQUIPE, CONSOLIDADO_COLABORADOR, COMPLETO")
            @RequestParam(required = false, defaultValue = "DETALHADO") TipoExportacaoRelatorio tipo,
            @Parameter(description = "Delimitador do CSV (padrão ';')")
            @RequestParam(required = false, defaultValue = ";") String delimitador
    ) {
        RelatorioFechamentoConsolidadoDTO relatorio = relatorioService.gerarRelatorioCampanha(id, null);
        byte[] csv = relatorioService.exportarCsv(relatorio, tipo, delimitador);

        String filename = String.format("relatorio_campanha_%d_%s.csv", id, tipo.name().toLowerCase());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }

    @GetMapping("/apuracoes/{apuracaoId}/relatorio")
    @Operation(
            summary = "Consolidar relatório de apuração definitiva específica (BUG-110 / BACK-15)",
            description = "Gera o relatório consolidado por canal, equipe e colaborador para um ID de apuração específico."
    )
    public ResponseEntity<RelatorioFechamentoConsolidadoDTO> obterRelatorioApuracaoPorId(
            @PathVariable @Parameter(description = "ID da campanha") Long id,
            @PathVariable @Parameter(description = "ID da apuração") UUID apuracaoId
    ) {
        RelatorioFechamentoConsolidadoDTO relatorio = relatorioService.gerarRelatorioCampanha(id, apuracaoId);
        return ResponseEntity.ok(relatorio);
    }

    @GetMapping(value = "/apuracoes/{apuracaoId}/exportar", produces = "text/csv")
    @Operation(
            summary = "Exportar relatório CSV de apuração definitiva específica (BUG-110 / BACK-15)",
            description = "Exporta o relatório consolidado de apuração histórica em CSV formatado UTF-8 com BOM."
    )
    public ResponseEntity<byte[]> exportarRelatorioApuracaoPorIdCsv(
            @PathVariable @Parameter(description = "ID da campanha") Long id,
            @PathVariable @Parameter(description = "ID da apuração") UUID apuracaoId,
            @Parameter(description = "Tipo de detalhamento: DETALHADO, CONSOLIDADO_CANAL, CONSOLIDADO_EQUIPE, CONSOLIDADO_COLABORADOR, COMPLETO")
            @RequestParam(required = false, defaultValue = "DETALHADO") TipoExportacaoRelatorio tipo,
            @Parameter(description = "Delimitador do CSV (padrão ';')")
            @RequestParam(required = false, defaultValue = ";") String delimitador
    ) {
        RelatorioFechamentoConsolidadoDTO relatorio = relatorioService.gerarRelatorioCampanha(id, apuracaoId);
        byte[] csv = relatorioService.exportarCsv(relatorio, tipo, delimitador);

        String filename = String.format("relatorio_campanha_%d_apuracao_%s.csv", id, apuracaoId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }
}

