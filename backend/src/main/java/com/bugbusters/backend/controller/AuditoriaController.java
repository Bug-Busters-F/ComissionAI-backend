package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.auditoria.AuditoriaResponseDTO;
import com.bugbusters.backend.dto.auditoria.FiltroAuditoriaDTO;
import com.bugbusters.backend.dto.auditoria.RegistroAuditoriaDTO;
import com.bugbusters.backend.dto.auditoria.ResumoAuditoriaDTO;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import com.bugbusters.backend.service.AuditoriaGeralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Controller REST para Auditoria Geral das Ações Relevantes (BUG-111 / BACK-16).
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoria Geral do Sistema", description = "Trilha unificada e imutável de auditoria de ações operacionais e compliance")
public class AuditoriaController {

    private final AuditoriaGeralService auditoriaService;

    @Autowired
    public AuditoriaController(AuditoriaGeralService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @PostMapping
    @Operation(
            summary = "Registrar ação auditável no sistema (BUG-111 / BACK-16)",
            description = "Registra um evento operacional relevante na trilha imutável de auditoria, capturando metadados, usuário, timestamp e estado dos dados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registro de auditoria gravado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de auditoria inválidos")
    })
    public ResponseEntity<AuditoriaResponseDTO> registrarAcao(
            @Valid @RequestBody RegistroAuditoriaDTO request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : null;
        String userAgent = servletRequest != null ? servletRequest.getHeader("User-Agent") : null;

        RegistroAuditoriaDTO enriched = new RegistroAuditoriaDTO(
                request.usuario(),
                request.perfilUsuario(),
                request.tipoAcao(),
                request.tipoEntidade(),
                request.entidadeId(),
                request.versaoEntidade(),
                request.descricao(),
                request.dadosAnteriores(),
                request.dadosNovos(),
                request.ipOrigem() != null ? request.ipOrigem() : ip,
                request.userAgent() != null ? request.userAgent() : userAgent,
                request.sucesso(),
                request.detalheErro()
        );

        AuditoriaResponseDTO response = auditoriaService.registrarAcao(enriched);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "Listar registros de auditoria com filtros dinâmicos e paginação (BUG-111 / BACK-16)",
            description = "Permite filtrar a trilha de auditoria por usuário, tipo de ação, entidade, período de datas e status de sucesso."
    )
    @ApiResponse(responseCode = "200", description = "Página de registros retornada com sucesso")
    public ResponseEntity<Page<AuditoriaResponseDTO>> listarAuditoria(
            @Parameter(description = "Filtro por usuário") @RequestParam(required = false) String usuario,
            @Parameter(description = "Filtro por tipo de ação") @RequestParam(required = false) TipoAcaoAuditoria tipoAcao,
            @Parameter(description = "Filtro por tipo de entidade") @RequestParam(required = false) TipoEntidadeAuditoria tipoEntidade,
            @Parameter(description = "Filtro por ID da entidade") @RequestParam(required = false) String entidadeId,
            @Parameter(description = "Data inicial (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataInicio,
            @Parameter(description = "Data final (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataFim,
            @Parameter(description = "Filtro por status de sucesso") @RequestParam(required = false) Boolean sucesso,
            @PageableDefault(size = 20, sort = "dataHora", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        FiltroAuditoriaDTO filtro = new FiltroAuditoriaDTO(usuario, tipoAcao, tipoEntidade, entidadeId, dataInicio, dataFim, sucesso);
        Page<AuditoriaResponseDTO> page = auditoriaService.listarAuditoria(pageable, filtro);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/protocolo/{protocolo}")
    @Operation(
            summary = "Buscar registro de auditoria pelo protocolo único (BUG-111 / BACK-16)",
            description = "Recupera os detalhes completos de um evento auditável a partir de seu protocolo UUID imutável."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registro encontrado"),
            @ApiResponse(responseCode = "404", description = "Protocolo não encontrado")
    })
    public ResponseEntity<AuditoriaResponseDTO> buscarPorProtocolo(
            @Parameter(description = "Protocolo oficial do evento de auditoria") @PathVariable UUID protocolo
    ) {
        AuditoriaResponseDTO response = auditoriaService.buscarPorProtocolo(protocolo);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/entidades/{tipoEntidade}/{entidadeId}")
    @Operation(
            summary = "Consultar trilha histórica de uma entidade específica (BUG-111 / BACK-16)",
            description = "Retorna todos os eventos auditados ocorridos sobre um determinado recurso (ex: CAMPANHA ID 10)."
    )
    @ApiResponse(responseCode = "200", description = "Trilha da entidade retornada com sucesso")
    public ResponseEntity<Page<AuditoriaResponseDTO>> listarPorEntidade(
            @Parameter(description = "Tipo da entidade") @PathVariable TipoEntidadeAuditoria tipoEntidade,
            @Parameter(description = "Identificador da entidade") @PathVariable String entidadeId,
            @PageableDefault(size = 20, sort = "dataHora", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AuditoriaResponseDTO> page = auditoriaService.listarPorEntidade(tipoEntidade, entidadeId, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/resumo")
    @Operation(
            summary = "Obter resumo executivo e métricas da trilha de auditoria (BUG-111 / BACK-16)",
            description = "Retorna contadores de eventos, sucessos, falhas e distribuição por tipo de ação e entidade."
    )
    @ApiResponse(responseCode = "200", description = "Resumo retornado com sucesso")
    public ResponseEntity<ResumoAuditoriaDTO> obterResumo() {
        ResumoAuditoriaDTO resumo = auditoriaService.obterResumo();
        return ResponseEntity.ok(resumo);
    }

    @GetMapping(value = "/exportar", produces = "text/csv")
    @Operation(
            summary = "Exportar trilha de auditoria em CSV com codificação UTF-8 com BOM (BUG-111 / BACK-16)",
            description = "Exporta os registros filtrados em arquivo CSV formatado para leitura direta no Excel."
    )
    @ApiResponse(responseCode = "200", description = "Arquivo CSV gerado com sucesso")
    public ResponseEntity<byte[]> exportarCsv(
            @Parameter(description = "Filtro por usuário") @RequestParam(required = false) String usuario,
            @Parameter(description = "Filtro por tipo de ação") @RequestParam(required = false) TipoAcaoAuditoria tipoAcao,
            @Parameter(description = "Filtro por tipo de entidade") @RequestParam(required = false) TipoEntidadeAuditoria tipoEntidade,
            @Parameter(description = "Filtro por ID da entidade") @RequestParam(required = false) String entidadeId,
            @Parameter(description = "Data inicial (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataInicio,
            @Parameter(description = "Data final (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataFim,
            @Parameter(description = "Filtro por status de sucesso") @RequestParam(required = false) Boolean sucesso,
            @Parameter(description = "Delimitador do CSV") @RequestParam(required = false, defaultValue = ";") String delimitador
    ) {
        FiltroAuditoriaDTO filtro = new FiltroAuditoriaDTO(usuario, tipoAcao, tipoEntidade, entidadeId, dataInicio, dataFim, sucesso);
        byte[] csv = auditoriaService.exportarCsv(filtro, delimitador);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditoria_geral.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }
}

