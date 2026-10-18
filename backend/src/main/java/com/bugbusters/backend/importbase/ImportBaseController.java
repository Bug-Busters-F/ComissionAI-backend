package com.bugbusters.backend.importbase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import org.springframework.http.CacheControl;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("importType") ImportType importType,
            @RequestParam(value = "competencia", required = false) String competencia
        ) {
            if (importType == ImportType.COMISSIONS) {
                if (competencia == null || !competencia.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                    throw new com.bugbusters.backend.exception.BusinessException(
                            "Parâmetro 'competencia' é obrigatório no formato YYYY-MM para importação de comissões."
                    );
                }
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
}
