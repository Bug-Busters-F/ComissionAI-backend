package com.bugbusters.backend.importbase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.bugbusters.backend.dto.preparacao.ArquivoOriginalDownloadDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.importbase.dto.ExclusaoLoteRequestDTO;
import com.bugbusters.backend.importbase.dto.ExclusaoLoteResponseDTO;
import com.bugbusters.backend.importbase.dto.InconsistenciaLoteDTO;
import com.bugbusters.backend.importbase.dto.LoteImportacaoResponseDTO;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Ingestão de Dados", description = "Importação transacional de planilhas Excel (RH, Vendas, Comissões)")
public class ImportBaseController {
    private final ImportService importService;

    @Autowired(required = false)
    private UsuarioAtualProvider usuarioAtualProvider;

    @Autowired
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

    @Operation(summary = "Listar lotes de importação", description = "Lista o histórico permanente de lotes de importação (concluídos, com falhas e excluídos) de forma paginada com filtros combináveis.")
    @ApiResponse(responseCode = "200", description = "Lotes listados com sucesso")
    @GetMapping
    public Page<LoteImportacaoResponseDTO> listarLotes(
            @Parameter(description = "Tipo de base (HR, SALES, COMISSIONS)", example = "SALES")
            @RequestParam(required = false) String tipoBase,
            @Parameter(description = "Status do lote (PENDENTE, PROCESSANDO, CONCLUIDO, FALHOU, EXCLUIDO)", example = "CONCLUIDO")
            @RequestParam(required = false) String status,
            @Parameter(description = "Competência no formato YYYY-MM", example = "2026-10")
            @RequestParam(required = false) String competencia,
            @Parameter(description = "Filtro por e-mail/identificador do autor ou responsável pela exclusão")
            @RequestParam(required = false) String usuario,
            @Parameter(description = "Origem da geração do lote (IMPORTACAO_DIRETA ou PREPARACAO_CAMPANHA)")
            @RequestParam(required = false) String origemLote,
            @Parameter(description = "Data/hora inicial da criação (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataInicio,
            @Parameter(description = "Data/hora final da criação (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataFim,
            @Parameter(description = "Quando true, retorna apenas lotes com validações/inconsistências ou rejeição integral")
            @RequestParam(required = false) Boolean somenteComInconsistencias,
            @Parameter(description = "Busca textual em nome do arquivo, idLote, reportId, mensagem ou responsável")
            @RequestParam(required = false) String busca,
            @Parameter(description = "Número da página, começando em 0", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamanho da página", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "criadoEm"));
        return importService.listarLotes(
                tipoBase, status, competencia, usuario, origemLote,
                dataInicio, dataFim, somenteComInconsistencias, busca, pageable
        );
    }

    @Operation(summary = "Listar validações e inconsistências persistidas de um lote", description = "Retorna paginadamente as falhas impeditivas e advertências registradas para o lote informado, inclusive em lotes com status FALHOU ou EXCLUIDO.")
    @ApiResponse(responseCode = "200", description = "Validações do lote listadas com sucesso")
    @ApiResponse(responseCode = "404", description = "Lote não encontrado")
    @GetMapping({"/{reportId}/validacoes", "/{reportId}/inconsistencias", "/lotes/{reportId}/validacoes", "/lotes/{reportId}/inconsistencias"})
    public Page<InconsistenciaLoteDTO> listarValidacoesLote(
            @Parameter(description = "reportId do lote", example = "123")
            @PathVariable long reportId,
            @Parameter(description = "Severidade (IMPEDITIVO, ADVERTENCIA, AVISO)")
            @RequestParam(required = false) String severidade,
            @Parameter(description = "Filtro por campo da inconsistência")
            @RequestParam(required = false) String campo,
            @PageableDefault(size = 50, sort = {"linha", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return importService.listarValidacoesLote(reportId, severidade, campo, pageable);
    }

    @Operation(summary = "Baixar arquivo original preservado de um lote", description = "Retorna o arquivo original associado ao reportId (preservado mesmo após falhas ou exclusão lógica do lote).")
    @ApiResponse(responseCode = "200", description = "Arquivo original retornado com sucesso")
    @ApiResponse(responseCode = "404", description = "Lote ou arquivo original não encontrado")
    @GetMapping({"/{reportId}/arquivo", "/lotes/{reportId}/arquivo"})
    public ResponseEntity<byte[]> baixarArquivoOriginal(
            @Parameter(description = "reportId do lote", example = "123")
            @PathVariable long reportId) {
        ArquivoOriginalDownloadDTO arquivo = importService.baixarArquivoOriginal(reportId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (arquivo.contentType() != null && !arquivo.contentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(arquivo.contentType());
            } catch (Exception ignored) {}
        }
        String nomeLimpo = arquivo.nomeArquivo() != null && !arquivo.nomeArquivo().isBlank()
                ? arquivo.nomeArquivo()
                : "lote-" + reportId + ".xlsx";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(nomeLimpo, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(arquivo.conteudo());
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
        String paliativo = (request != null && request.usuario() != null && !request.usuario().isBlank())
                ? request.usuario().trim()
                : "SISTEMA";
        String usuario = usuarioAtualProvider != null
                ? usuarioAtualProvider.obterUsuarioAtual(paliativo)
                : paliativo;
        String motivo = request != null ? request.motivo() : null;
        ExclusaoLoteResponseDTO response = importService.excluirLote(reportId, usuario, motivo);
        return ResponseEntity.ok(response);
    }
}
