package com.bugbusters.backend.controller;

import com.bugbusters.backend.auth.PapelUsuario;
import com.bugbusters.backend.auth.Usuario;
import com.bugbusters.backend.dto.auditoria.AuditoriaResponseDTO;
import com.bugbusters.backend.dto.auditoria.AuditoriaUsuarioResumoDTO;
import com.bugbusters.backend.dto.auditoria.FiltroAuditoriaDTO;
import com.bugbusters.backend.dto.auditoria.RegistroAuditoriaDTO;
import com.bugbusters.backend.dto.auditoria.ResumoAuditoriaDTO;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import com.bugbusters.backend.service.AuditoriaGeralService;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Controller REST para Auditoria Geral das Ações Relevantes (BUG-111 / BACK-16 / BACK-17).
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoria Geral do Sistema", description = "Trilha unificada e imutável de auditoria de ações operacionais e compliance")
public class AuditoriaController {

    private final AuditoriaGeralService auditoriaService;

    @Autowired(required = false)
    private UsuarioAtualProvider usuarioAtualProvider = (paliativo) -> (paliativo != null && !paliativo.isBlank()) ? paliativo.trim() : "SISTEMA";

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
        String usuarioAutenticado = usuarioAtualProvider.obterUsuarioAtual(request.usuario());
        String perfilAutenticado = resolverPerfilAutenticado(request.perfilUsuario());

        RegistroAuditoriaDTO enriched = new RegistroAuditoriaDTO(
                usuarioAutenticado,
                perfilAutenticado,
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

    private String resolverPerfilAutenticado(String perfilPaliativo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario u && u.getPapel() != null) {
            return u.getPapel().name();
        }
        return perfilPaliativo;
    }

    @GetMapping
    @Operation(
            summary = "Listar registros de auditoria com filtros dinâmicos, relacionamentos e autorização no servidor (BACK-17)",
            description = "Administrador vê tudo; gerente vê apenas ações dos analistas; auditor vê analistas e gerente."
    )
    @ApiResponse(responseCode = "200", description = "Página de registros retornada com sucesso")
    public ResponseEntity<Page<AuditoriaResponseDTO>> listarAuditoria(
            @Parameter(description = "Filtro por usuário (e-mail/identificador)") @RequestParam(required = false) String usuario,
            @Parameter(description = "Filtro por perfil do usuário") @RequestParam(required = false) String perfilUsuario,
            @Parameter(description = "Filtro por tipo de ação") @RequestParam(required = false) TipoAcaoAuditoria tipoAcao,
            @Parameter(description = "Filtro por tipo de entidade") @RequestParam(required = false) TipoEntidadeAuditoria tipoEntidade,
            @Parameter(description = "Filtro por ID da entidade") @RequestParam(required = false) String entidadeId,
            @Parameter(description = "Filtro por ID da campanha relacionada") @RequestParam(required = false) Long campanhaId,
            @Parameter(description = "Filtro por reportId do lote relacionado") @RequestParam(required = false) Long reportId,
            @Parameter(description = "Filtro por idLote relacionado") @RequestParam(required = false) String idLote,
            @Parameter(description = "Filtro por envioArquivoId relacionado") @RequestParam(required = false) Long envioArquivoId,
            @Parameter(description = "Filtro por arquivoImportacaoId relacionado") @RequestParam(required = false) UUID arquivoImportacaoId,
            @Parameter(description = "Filtro por protocolo de cálculo relacionado") @RequestParam(required = false) UUID protocoloCalculo,
            @Parameter(description = "Filtro por execução assíncrona de cálculo relacionada") @RequestParam(required = false) UUID execucaoCalculoId,
            @Parameter(description = "Filtro por fechamento relacionado") @RequestParam(required = false) UUID fechamentoId,
            @Parameter(description = "Filtro por competência (YYYY-MM)") @RequestParam(required = false) String competencia,
            @Parameter(description = "Data inicial (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataInicio,
            @Parameter(description = "Data final (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataFim,
            @Parameter(description = "Filtro por status de sucesso") @RequestParam(required = false) Boolean sucesso,
            @Parameter(description = "Busca textual em descrição, usuário, campanha ou arquivo") @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "dataHora", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        FiltroAuditoriaDTO filtro = new FiltroAuditoriaDTO(
                usuario, perfilUsuario, tipoAcao, tipoEntidade, entidadeId,
                campanhaId, reportId, idLote, envioArquivoId, arquivoImportacaoId,
                protocoloCalculo, execucaoCalculoId, fechamentoId, competencia,
                dataInicio, dataFim, sucesso, busca
        );
        return ResponseEntity.ok(auditoriaService.listarAuditoria(pageable, filtro));
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    @Operation(
            summary = "Buscar detalhes completos de evento de auditoria pelo ID (BACK-17)",
            description = "Recupera evento auditável e seus relacionamentos com campanhas, arquivos e cálculos respeitando autorização por perfil."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registro encontrado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão para este registro"),
            @ApiResponse(responseCode = "404", description = "ID não encontrado")
    })
    public ResponseEntity<AuditoriaResponseDTO> buscarPorId(
            @Parameter(description = "UUID do registro de auditoria") @PathVariable UUID id
    ) {
        return ResponseEntity.ok(auditoriaService.buscarPorId(id));
    }

    @GetMapping("/protocolo/{protocolo}")
    @Operation(
            summary = "Buscar registro de auditoria pelo protocolo único (BUG-111 / BACK-16 / BACK-17)",
            description = "Recupera os detalhes completos e relacionamentos de um evento auditável a partir de seu protocolo UUID imutável."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registro encontrado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão para este registro"),
            @ApiResponse(responseCode = "404", description = "Protocolo não encontrado")
    })
    public ResponseEntity<AuditoriaResponseDTO> buscarPorProtocolo(
            @Parameter(description = "Protocolo oficial do evento de auditoria") @PathVariable UUID protocolo
    ) {
        AuditoriaResponseDTO response = auditoriaService.buscarPorProtocolo(protocolo);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/usuarios")
    @Operation(
            summary = "Listar usuários com logs visíveis conforme autorização do servidor (BACK-17 / FRONT-12)",
            description = "Retorna resumo de atividade por usuário autorizado (administrador vê todos; gerente vê analistas; auditor vê analistas e gerente)."
    )
    @ApiResponse(responseCode = "200", description = "Página de usuários e métricas retornada com sucesso")
    public ResponseEntity<Page<AuditoriaUsuarioResumoDTO>> listarUsuariosVisiveis(
            @Parameter(description = "Busca por nome ou e-mail") @RequestParam(required = false) String busca,
            @Parameter(description = "Filtro por papel") @RequestParam(required = false) PapelUsuario papel,
            @Parameter(description = "Filtro por conta ativa") @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return ResponseEntity.ok(auditoriaService.listarUsuariosVisiveis(busca, papel, ativo, pageable));
    }

    @GetMapping("/usuarios/{usuario}")
    @Operation(
            summary = "Consultar logs paginados de um usuário específico (BACK-17 / FRONT-12)",
            description = "Aceita e-mail/identificador ou UUID do usuário e retorna eventos detalhados conforme autorização no servidor."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de eventos do usuário retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissão para consultar logs deste usuário"),
            @ApiResponse(responseCode = "404", description = "Usuário informado por UUID não encontrado")
    })
    public ResponseEntity<Page<AuditoriaResponseDTO>> listarPorUsuario(
            @Parameter(description = "E-mail/identificador ou UUID do usuário") @PathVariable String usuario,
            @Parameter(description = "Filtro por tipo de ação") @RequestParam(required = false) TipoAcaoAuditoria tipoAcao,
            @Parameter(description = "Filtro por tipo de entidade") @RequestParam(required = false) TipoEntidadeAuditoria tipoEntidade,
            @Parameter(description = "Filtro por campanha relacionada") @RequestParam(required = false) Long campanhaId,
            @Parameter(description = "Filtro por reportId do lote relacionado") @RequestParam(required = false) Long reportId,
            @Parameter(description = "Filtro por idLote relacionado") @RequestParam(required = false) String idLote,
            @Parameter(description = "Filtro por protocolo de cálculo relacionado") @RequestParam(required = false) UUID protocoloCalculo,
            @Parameter(description = "Filtro por competência (YYYY-MM)") @RequestParam(required = false) String competencia,
            @Parameter(description = "Data inicial (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataInicio,
            @Parameter(description = "Data final (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataFim,
            @Parameter(description = "Filtro por status de sucesso") @RequestParam(required = false) Boolean sucesso,
            @Parameter(description = "Busca textual") @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "dataHora", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        FiltroAuditoriaDTO filtro = new FiltroAuditoriaDTO(
                null, null, tipoAcao, tipoEntidade, null,
                campanhaId, reportId, idLote, null, null,
                protocoloCalculo, null, null, competencia,
                dataInicio, dataFim, sucesso, busca
        );
        return ResponseEntity.ok(auditoriaService.listarPorUsuario(usuario, filtro, pageable));
    }

    @GetMapping("/entidades/{tipoEntidade}/{entidadeId}")
    @Operation(
            summary = "Consultar trilha histórica de uma entidade específica (BUG-111 / BACK-16 / BACK-17)",
            description = "Retorna todos os eventos auditados autorizados para o perfil sobre um recurso específico."
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
            summary = "Obter resumo executivo e métricas da trilha de auditoria no escopo do perfil (BUG-111 / BACK-17)",
            description = "Retorna contadores de eventos, sucessos, falhas e distribuição respeitando a visibilidade do leitor."
    )
    @ApiResponse(responseCode = "200", description = "Resumo retornado com sucesso")
    public ResponseEntity<ResumoAuditoriaDTO> obterResumo(
            @Parameter(description = "Filtro opcional por usuário") @RequestParam(required = false) String usuario,
            @Parameter(description = "Filtro opcional por perfil") @RequestParam(required = false) String perfilUsuario,
            @Parameter(description = "Filtro por tipo de ação") @RequestParam(required = false) TipoAcaoAuditoria tipoAcao,
            @Parameter(description = "Filtro por tipo de entidade") @RequestParam(required = false) TipoEntidadeAuditoria tipoEntidade,
            @Parameter(description = "Filtro opcional por campanha") @RequestParam(required = false) Long campanhaId,
            @Parameter(description = "Filtro por reportId") @RequestParam(required = false) Long reportId,
            @Parameter(description = "Filtro por idLote") @RequestParam(required = false) String idLote,
            @Parameter(description = "Filtro por envioArquivoId") @RequestParam(required = false) Long envioArquivoId,
            @Parameter(description = "Filtro por arquivoImportacaoId") @RequestParam(required = false) UUID arquivoImportacaoId,
            @Parameter(description = "Filtro por protocoloCalculo") @RequestParam(required = false) UUID protocoloCalculo,
            @Parameter(description = "Filtro por execucaoCalculoId") @RequestParam(required = false) UUID execucaoCalculoId,
            @Parameter(description = "Filtro por fechamentoId") @RequestParam(required = false) UUID fechamentoId,
            @Parameter(description = "Filtro por competência (YYYY-MM)") @RequestParam(required = false) String competencia,
            @Parameter(description = "Data inicial (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataInicio,
            @Parameter(description = "Data final (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataFim,
            @Parameter(description = "Filtro por status de sucesso") @RequestParam(required = false) Boolean sucesso,
            @Parameter(description = "Busca textual") @RequestParam(required = false) String busca
    ) {
        boolean semFiltro = usuario == null && perfilUsuario == null && tipoAcao == null && tipoEntidade == null
                && campanhaId == null && reportId == null && idLote == null && envioArquivoId == null
                && arquivoImportacaoId == null && protocoloCalculo == null && execucaoCalculoId == null
                && fechamentoId == null && competencia == null && dataInicio == null && dataFim == null
                && sucesso == null && busca == null;
        FiltroAuditoriaDTO filtro = semFiltro
                ? null
                : new FiltroAuditoriaDTO(usuario, perfilUsuario, tipoAcao, tipoEntidade, null, campanhaId,
                        reportId, idLote, envioArquivoId, arquivoImportacaoId, protocoloCalculo,
                        execucaoCalculoId, fechamentoId, competencia, dataInicio, dataFim, sucesso, busca);
        return ResponseEntity.ok(auditoriaService.obterResumo(filtro));
    }

    @GetMapping(value = "/exportar", produces = "text/csv")
    @Operation(
            summary = "Exportar trilha de auditoria autorizada em CSV com UTF-8 BOM (BUG-111 / BACK-17)",
            description = "Exporta apenas registros visíveis para o perfil autenticado em arquivo CSV."
    )
    @ApiResponse(responseCode = "200", description = "Arquivo CSV gerado com sucesso")
    public ResponseEntity<byte[]> exportarCsv(
            @Parameter(description = "Filtro por usuário") @RequestParam(required = false) String usuario,
            @Parameter(description = "Filtro por perfil") @RequestParam(required = false) String perfilUsuario,
            @Parameter(description = "Filtro por tipo de ação") @RequestParam(required = false) TipoAcaoAuditoria tipoAcao,
            @Parameter(description = "Filtro por tipo de entidade") @RequestParam(required = false) TipoEntidadeAuditoria tipoEntidade,
            @Parameter(description = "Filtro por ID da entidade") @RequestParam(required = false) String entidadeId,
            @Parameter(description = "Filtro por campanha") @RequestParam(required = false) Long campanhaId,
            @Parameter(description = "Filtro por reportId") @RequestParam(required = false) Long reportId,
            @Parameter(description = "Filtro por idLote") @RequestParam(required = false) String idLote,
            @Parameter(description = "Filtro por envioArquivoId") @RequestParam(required = false) Long envioArquivoId,
            @Parameter(description = "Filtro por arquivoImportacaoId") @RequestParam(required = false) UUID arquivoImportacaoId,
            @Parameter(description = "Filtro por protocolo de cálculo") @RequestParam(required = false) UUID protocoloCalculo,
            @Parameter(description = "Filtro por execucaoCalculoId") @RequestParam(required = false) UUID execucaoCalculoId,
            @Parameter(description = "Filtro por fechamentoId") @RequestParam(required = false) UUID fechamentoId,
            @Parameter(description = "Filtro por competência (YYYY-MM)") @RequestParam(required = false) String competencia,
            @Parameter(description = "Data inicial (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataInicio,
            @Parameter(description = "Data final (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataFim,
            @Parameter(description = "Filtro por status de sucesso") @RequestParam(required = false) Boolean sucesso,
            @Parameter(description = "Busca textual") @RequestParam(required = false) String busca,
            @Parameter(description = "Delimitador do CSV") @RequestParam(required = false, defaultValue = ";") String delimitador
    ) {
        FiltroAuditoriaDTO filtro = new FiltroAuditoriaDTO(
                usuario, perfilUsuario, tipoAcao, tipoEntidade, entidadeId,
                campanhaId, reportId, idLote, envioArquivoId, arquivoImportacaoId,
                protocoloCalculo, execucaoCalculoId, fechamentoId, competencia,
                dataInicio, dataFim, sucesso, busca
        );
        byte[] csv = auditoriaService.exportarCsv(filtro, delimitador);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"auditoria_geral.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }
}
