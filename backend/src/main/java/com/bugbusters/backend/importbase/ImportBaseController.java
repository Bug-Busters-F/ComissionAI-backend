package com.bugbusters.backend.importbase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.importbase.dto.ExclusaoLoteRequestDTO;
import com.bugbusters.backend.importbase.dto.ExclusaoLoteResponseDTO;
import com.bugbusters.backend.importbase.dto.LoteImportacaoResponseDTO;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Ingestão de Dados", description = "Importação transacional de planilhas Excel (RH, Vendas, Comissões)")
public class ImportBaseController {
    private final ImportService importService;

    public ImportBaseController(ImportService importService) {
        this.importService = importService;
    }

    @Operation(summary = "Iniciar importação assíncrona", description = "Recebe a planilha Excel e retorna reportId. Consulte GET /api/v1/imports/{reportId} para acompanhar o processamento. Falhas de validação são informadas no relatório e desfazem toda a importação.")
    @ApiResponse(responseCode = "202", description = "Importação recebida; consulte a URL no header Location")
    @ApiResponse(responseCode = "400", description = "Upload vazio ou parâmetros inválidos")
    @ApiResponse(responseCode = "409", description = "Já existe lote ativo para o mesmo arquivo e competência")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("importType") ImportType importType,
            @RequestParam(value = "competencia", required = false) String competencia
    ) {
        if (importType == ImportType.COMISSIONS) {
            if (competencia == null || !competencia.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                throw new BusinessException("Parâmetro 'competencia' é obrigatório no formato YYYY-MM para importação de comissões.");
            }
        } else if (importType == ImportType.SALES) {
            if (competencia == null || !competencia.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                throw new BusinessException("Parâmetro 'competencia' é obrigatório no formato YYYY-MM para importação de vendas.");
            }
        } else if (importType == ImportType.HR) {
            competencia = null;
        }

        ImportResponse response = importService.processImport(file, importType, competencia);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/imports/" + response.reportId()))
                .body(response);
    }

    @Operation(summary = "Consultar status e resultado da importação", description = "Use o reportId retornado pelo upload. Consulte periodicamente até CONCLUIDO ou FALHOU. totalLinhas só é informado ao terminar; em caso de falha, vale zero porque a importação é transacional.")
    @ApiResponse(responseCode = "200", description = "Status atual da importação")
    @ApiResponse(responseCode = "404", description = "Relatório não encontrado")
    @GetMapping("/{reportId}")
    public ResponseEntity<ImportResponse> report(
            @Parameter(description = "reportId retornado pelo upload", example = "123")
            @PathVariable long reportId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(importService.getReport(reportId));
    }

    @Operation(summary = "Consultar detalhes do lote persistido", description = "Retorna os detalhes completos do lote de importação persistido buscando pelo reportId.")
    @ApiResponse(responseCode = "200", description = "Lote encontrado com sucesso")
    @ApiResponse(responseCode = "404", description = "Lote não encontrado")
    @GetMapping("/lotes/{reportId}")
    public ResponseEntity<LoteImportacaoResponseDTO> consultarLote(
            @Parameter(description = "reportId retornado pelo upload", example = "123")
            @PathVariable long reportId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(importService.consultarLote(reportId));
    }

    @Operation(summary = "Listar lotes de importação", description = "Lista os lotes de importação persistidos de forma paginada com filtros opcionais por tipo de base, status e competência.")
    @ApiResponse(responseCode = "200", description = "Lotes listados com sucesso")
    @GetMapping
    public Page<LoteImportacaoResponseDTO> listarLotes(
            @Parameter(description = "Tipo de base (HR, SALES, COMISSIONS)", example = "SALES")
            @RequestParam(required = false) String tipoBase,
            @Parameter(description = "Status do lote (PENDENTE, PROCESSANDO, CONCLUIDO, FALHOU, EXCLUIDO)", example = "CONCLUIDO")
            @RequestParam(required = false) String status,
            @Parameter(description = "Competência no formato YYYY-MM", example = "2026-10")
            @RequestParam(required = false) String competencia,
            @Parameter(description = "Número da página, começando em 0", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return importService.listarLotes(tipoBase, status, competencia, pageable);
    }

    @Operation(summary = "Excluir ou cancelar lote de importação", description = "Executa a exclusão transacional dos dados importados pelo lote. Valida dependências com execuções em andamento, vendas, cálculos e logs financeiros.")
    @ApiResponse(responseCode = "200", description = "Lote excluído com sucesso e dados removidos")
    @ApiResponse(responseCode = "404", description = "Lote não encontrado")
    @ApiResponse(responseCode = "409", description = "Exclusão bloqueada devido a dependências ou execução em andamento")
    @DeleteMapping("/{reportId}")
    public ResponseEntity<ExclusaoLoteResponseDTO> excluirLote(
            @Parameter(description = "reportId retornado pelo upload", example = "123")
            @PathVariable long reportId,
            @RequestBody(required = false) ExclusaoLoteRequestDTO request) {
        String usuario = (request != null && request.usuario() != null && !request.usuario().isBlank())
                ? request.usuario().trim()
                : "SISTEMA";
        String motivo = request != null ? request.motivo() : null;
        ExclusaoLoteResponseDTO response = importService.excluirLote(reportId, usuario, motivo);
        return ResponseEntity.ok(response);
    }
}
