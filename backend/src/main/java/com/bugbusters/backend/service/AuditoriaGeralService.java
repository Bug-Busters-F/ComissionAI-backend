package com.bugbusters.backend.service;

import com.bugbusters.backend.auth.PapelUsuario;
import com.bugbusters.backend.auth.PerfilPermissoes;
import com.bugbusters.backend.auth.Usuario;
import com.bugbusters.backend.auth.UsuarioRepository;
import com.bugbusters.backend.dto.auditoria.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Serviço responsável pelo registro e consulta da trilha de auditoria geral (BUG-111 / BACK-16 / BACK-17).
 * Aplica autorização por perfil no servidor, resolve metadados HTTP e hidrata relacionamentos
 * com campanhas, arquivos/lotes e cálculos.
 */
@Service
public class AuditoriaGeralService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaGeralService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AuditoriaGeralRepository repository;

    @Autowired(required = false)
    private UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private CampanhaRepository campanhaRepository;

    @Autowired(required = false)
    private EnvioArquivoRepository envioArquivoRepository;

    @Autowired(required = false)
    private ExecucaoCalculoRepository execucaoCalculoRepository;

    @Autowired(required = false)
    private CampanhaApuracaoRepository campanhaApuracaoRepository;

    @Autowired(required = false)
    private FechamentoCompetenciaRepository fechamentoCompetenciaRepository;

    @Autowired(required = false)
    private SimulacaoRepository simulacaoRepository;

    @Autowired(required = false)
    private LogCalculoRepository logCalculoRepository;

    public AuditoriaGeralService(AuditoriaGeralRepository repository) {
        this.repository = repository;
    }

    @Autowired
    public AuditoriaGeralService(
            AuditoriaGeralRepository repository,
            @Autowired(required = false) UsuarioRepository usuarioRepository,
            @Autowired(required = false) CampanhaRepository campanhaRepository,
            @Autowired(required = false) EnvioArquivoRepository envioArquivoRepository,
            @Autowired(required = false) ExecucaoCalculoRepository execucaoCalculoRepository,
            @Autowired(required = false) CampanhaApuracaoRepository campanhaApuracaoRepository,
            @Autowired(required = false) FechamentoCompetenciaRepository fechamentoCompetenciaRepository,
            @Autowired(required = false) SimulacaoRepository simulacaoRepository,
            @Autowired(required = false) LogCalculoRepository logCalculoRepository
    ) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.campanhaRepository = campanhaRepository;
        this.envioArquivoRepository = envioArquivoRepository;
        this.execucaoCalculoRepository = execucaoCalculoRepository;
        this.campanhaApuracaoRepository = campanhaApuracaoRepository;
        this.fechamentoCompetenciaRepository = fechamentoCompetenciaRepository;
        this.simulacaoRepository = simulacaoRepository;
        this.logCalculoRepository = logCalculoRepository;
    }

    /**
     * Registra formalmente uma ação auditável a partir do DTO REST.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditoriaResponseDTO registrarAcao(RegistroAuditoriaDTO dto) {
        if (dto == null) {
            throw new BusinessException("Dados de auditoria não podem ser nulos.");
        }
        AuditoriaEventoContexto ctx = AuditoriaEventoContexto.builder()
                .usuario(dto.usuario())
                .perfilUsuario(dto.perfilUsuario())
                .tipoAcao(dto.tipoAcao())
                .tipoEntidade(dto.tipoEntidade())
                .entidadeId(dto.entidadeId())
                .versaoEntidade(dto.versaoEntidade())
                .descricao(dto.descricao())
                .dadosAnteriores(dto.dadosAnteriores())
                .dadosNovos(dto.dadosNovos())
                .ipOrigem(dto.ipOrigem())
                .userAgent(dto.userAgent())
                .sucesso(dto.sucesso())
                .detalheErro(dto.detalheErro());
        inferirReferenciaPorEntidade(ctx);
        return registrarEvento(ctx);
    }

    /**
     * Registra uma ação auditável interna enriquecida com relacionamentos explícitos.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditoriaResponseDTO registrarEvento(AuditoriaEventoContexto ctx) {
        if (ctx == null) {
            throw new BusinessException("Contexto de auditoria não pode ser nulo.");
        }

        String usuarioEfetivo = resolverUsuarioEfetivo(ctx.getUsuario());
        String perfilEfetivo = resolverPerfilEfetivo(ctx.getPerfilUsuario(), usuarioEfetivo);
        String ipEfetivo = ctx.getIpOrigem() != null && !ctx.getIpOrigem().isBlank() ? ctx.getIpOrigem() : obterIpHttpAtual();
        String userAgentEfetivo = ctx.getUserAgent() != null && !ctx.getUserAgent().isBlank() ? ctx.getUserAgent() : obterUserAgentHttpAtual();

        AuditoriaGeral audit = new AuditoriaGeral(
                usuarioEfetivo,
                perfilEfetivo,
                ctx.getTipoAcao(),
                ctx.getTipoEntidade(),
                ctx.getEntidadeId(),
                ctx.getVersaoEntidade(),
                ctx.getDescricao(),
                ctx.getDadosAnteriores(),
                ctx.getDadosNovos(),
                ipEfetivo,
                userAgentEfetivo,
                ctx.getSucesso() != null ? ctx.getSucesso() : true,
                ctx.getDetalheErro()
        );

        inferirReferenciaPorEntidade(ctx);
        preencherVinculos(audit, ctx);

        AuditoriaGeral salvo = repository.save(audit);
        log.info("Auditoria registrada: [Protocolo: {}] [{}] [{}] por {} ({})",
                salvo.getProtocolo(), salvo.getTipoAcao(), salvo.getTipoEntidade(),
                salvo.getUsuario(), salvo.getPerfilUsuario());

        List<AuditoriaResponseDTO> hidratado = hidratarRegistros(List.of(salvo));
        return hidratado.get(0);
    }

    /**
     * Registra evento em nova transação independente (útil para falhas ou jobs batch após rollback).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditoriaResponseDTO registrarEventoIsolado(AuditoriaEventoContexto ctx) {
        return registrarEvento(ctx);
    }

    /**
     * Sobrecarga de conveniência para registros internos rápidos.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditoriaResponseDTO registrarAcao(
            String usuario,
            TipoAcaoAuditoria tipoAcao,
            TipoEntidadeAuditoria tipoEntidade,
            String entidadeId,
            Integer versaoEntidade,
            String descricao
    ) {
        return registrarAcao(usuario, tipoAcao, tipoEntidade, entidadeId, versaoEntidade, descricao, null);
    }

    /**
     * Sobrecarga com payload novo.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditoriaResponseDTO registrarAcao(
            String usuario,
            TipoAcaoAuditoria tipoAcao,
            TipoEntidadeAuditoria tipoEntidade,
            String entidadeId,
            Integer versaoEntidade,
            String descricao,
            String dadosNovos
    ) {
        AuditoriaEventoContexto ctx = AuditoriaEventoContexto.builder()
                .usuario(usuario)
                .tipoAcao(tipoAcao)
                .tipoEntidade(tipoEntidade)
                .entidadeId(entidadeId)
                .versaoEntidade(versaoEntidade)
                .descricao(descricao)
                .dadosNovos(dadosNovos)
                .sucesso(true);
        inferirReferenciaPorEntidade(ctx);
        return registrarEvento(ctx);
    }

    /**
     * Consulta paginada com filtros dinâmicos e autorização aplicada no servidor.
     */
    @Transactional(readOnly = true)
    public Page<AuditoriaResponseDTO> listarAuditoria(Pageable pageable, FiltroAuditoriaDTO filtro) {
        return listarAuditoriaInterno(pageable, filtro, false);
    }

    private Page<AuditoriaResponseDTO> listarAuditoriaInterno(Pageable pageable, FiltroAuditoriaDTO filtro, boolean usuarioExato) {
        validarIntervaloFiltro(filtro);
        List<String> perfisPermitidos = perfisPermitidosDaSessao();
        Specification<AuditoriaGeral> spec = criarSpecification(filtro, perfisPermitidos, usuarioExato);
        Page<AuditoriaGeral> page = repository.findAll(spec, pageable);
        List<AuditoriaResponseDTO> dtos = hidratarRegistros(page.getContent());
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    /**
     * Busca detalhada de um evento pelo UUID primário com verificação de permissão no servidor.
     */
    @Transactional(readOnly = true)
    public AuditoriaResponseDTO buscarPorId(UUID id) {
        AuditoriaGeral audit = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de auditoria não encontrado para o ID: " + id));
        validarAcessoAoRegistro(audit);
        return hidratarRegistros(List.of(audit)).get(0);
    }

    /**
     * Busca um registro de auditoria pelo protocolo único com verificação de permissão no servidor.
     */
    @Transactional(readOnly = true)
    public AuditoriaResponseDTO buscarPorProtocolo(UUID protocolo) {
        AuditoriaGeral audit = repository.findByProtocolo(protocolo)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de auditoria não encontrado para o protocolo: " + protocolo));
        validarAcessoAoRegistro(audit);
        return hidratarRegistros(List.of(audit)).get(0);
    }

    /**
     * Lista a trilha histórica de auditoria de uma entidade específica, aplicando escopo de perfil.
     */
    @Transactional(readOnly = true)
    public Page<AuditoriaResponseDTO> listarPorEntidade(
            TipoEntidadeAuditoria tipoEntidade,
            String entidadeId,
            Pageable pageable
    ) {
        FiltroAuditoriaDTO filtro = new FiltroAuditoriaDTO(
                null, tipoAcaoNula(), tipoEntidade, entidadeId, null, null, null
        );
        return listarAuditoria(pageable, filtro);
    }

    /**
     * Lista os usuários e seus respectivos totais de logs visíveis conforme a hierarquia do leitor autenticado.
     */
    @Transactional(readOnly = true)
    public Page<AuditoriaUsuarioResumoDTO> listarUsuariosVisiveis(
            String busca,
            PapelUsuario papelFiltro,
            Boolean ativoFiltro,
            Pageable pageable
    ) {
        List<String> perfisPermitidos = perfisPermitidosDaSessao();
        String papelFiltroStr = papelFiltro != null ? papelFiltro.name() : null;
        if (papelFiltroStr != null && perfisPermitidos != null && !perfisPermitidos.contains(papelFiltroStr)) {
            return Page.empty(pageable);
        }

        Map<String, UsuarioAgregado> mapa = new LinkedHashMap<>();

        if (usuarioRepository != null) {
            for (Usuario u : usuarioRepository.findAll()) {
                String papelNome = u.getPapel() != null ? u.getPapel().name() : null;
                if (perfisPermitidos != null && !perfisPermitidos.contains(papelNome)) {
                    continue;
                }
                String chave = u.getEmail() != null ? u.getEmail().trim().toLowerCase() : u.getId().toString();
                UsuarioAgregado agg = mapa.computeIfAbsent(chave, k -> new UsuarioAgregado(u.getEmail()));
                agg.usuarioId = u.getId();
                agg.nome = u.getNome();
                agg.email = u.getEmail();
                agg.papel = papelNome;
                agg.nomePerfil = u.getPapel() != null ? u.getPapel().getNome() : null;
                agg.ativo = u.isAtivo();
            }
        }

        for (Object[] row : repository.agruparAtividadePorUsuarioEPerfil()) {
            String emailKey = row[0] != null ? String.valueOf(row[0]).trim().toLowerCase() : null;
            String perfilLog = row[1] != null ? String.valueOf(row[1]).trim() : null;
            if (emailKey == null || emailKey.isBlank()) continue;
            if (perfisPermitidos != null && (perfilLog == null || !perfisPermitidos.contains(perfilLog))) {
                continue;
            }
            long total = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            long sucessos = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            OffsetDateTime ultima = row[4] instanceof OffsetDateTime odt ? odt : null;

            UsuarioAgregado agg = mapa.computeIfAbsent(emailKey, k -> new UsuarioAgregado(emailKey));
            if (agg.papel == null) agg.papel = perfilLog;
            if (agg.nomePerfil == null) agg.nomePerfil = resolverNomePerfil(perfilLog);
            agg.totalEventosAuditoria += total;
            agg.totalSucessos += sucessos;
            agg.totalFalhas += Math.max(0L, total - sucessos);
            if (ultima != null && (agg.ultimaAtividadeEm == null || ultima.isAfter(agg.ultimaAtividadeEm))) {
                agg.ultimaAtividadeEm = ultima;
            }
        }

        if (logCalculoRepository != null) {
            for (Object[] row : logCalculoRepository.agruparAtividadePorUsuarioEPerfil()) {
                String emailKey = row[0] != null ? String.valueOf(row[0]).trim().toLowerCase() : null;
                String perfilLog = row[1] != null ? String.valueOf(row[1]).trim() : null;
                if (emailKey == null || emailKey.isBlank()) continue;
                if (perfisPermitidos != null && (perfilLog == null || !perfisPermitidos.contains(perfilLog))) {
                    continue;
                }
                long totalCalc = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                OffsetDateTime ultima = row[3] instanceof OffsetDateTime odt ? odt : null;

                UsuarioAgregado agg = mapa.computeIfAbsent(emailKey, k -> new UsuarioAgregado(emailKey));
                if (agg.papel == null) agg.papel = perfilLog;
                if (agg.nomePerfil == null) agg.nomePerfil = resolverNomePerfil(perfilLog);
                agg.totalLogsCalculo += totalCalc;
                if (ultima != null && (agg.ultimaAtividadeEm == null || ultima.isAfter(agg.ultimaAtividadeEm))) {
                    agg.ultimaAtividadeEm = ultima;
                }
            }
        }

        String buscaNorm = (busca != null && !busca.isBlank()) ? busca.trim().toLowerCase() : null;
        List<AuditoriaUsuarioResumoDTO> filtrados = mapa.values().stream()
                .filter(agg -> buscaNorm == null
                        || containsIgnoreCase(agg.usuario, buscaNorm)
                        || containsIgnoreCase(agg.nome, buscaNorm)
                        || containsIgnoreCase(agg.email, buscaNorm))
                .filter(agg -> papelFiltroStr == null || papelFiltroStr.equalsIgnoreCase(agg.papel))
                .filter(agg -> ativoFiltro == null || Objects.equals(agg.ativo, ativoFiltro))
                .sorted(Comparator
                        .comparing((UsuarioAgregado u) -> u.ultimaAtividadeEm, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(u -> u.usuario, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(UsuarioAgregado::toDto)
                .toList();

        int start = (int) Math.min(pageable.getOffset(), filtrados.size());
        int end = Math.min(start + pageable.getPageSize(), filtrados.size());
        return new PageImpl<>(filtrados.subList(start, end), pageable, filtrados.size());
    }

    /**
     * Lista logs paginados de um usuário específico (e-mail ou UUID), garantindo autorização no servidor.
     */
    @Transactional(readOnly = true)
    public Page<AuditoriaResponseDTO> listarPorUsuario(
            String usuarioOuId,
            FiltroAuditoriaDTO filtro,
            Pageable pageable
    ) {
        if (usuarioOuId == null || usuarioOuId.isBlank()) {
            throw new BusinessException("Identificador do usuário é obrigatório.");
        }

        List<String> perfisPermitidos = perfisPermitidosDaSessao();
        String usuarioAlvo = usuarioOuId.trim();
        Usuario entidadeAlvo = resolverUsuarioAlvo(usuarioAlvo);
        if (entidadeAlvo != null) {
            usuarioAlvo = entidadeAlvo.getEmail();
        }

        if (perfisPermitidos != null && entidadeAlvo == null && "SISTEMA".equalsIgnoreCase(usuarioAlvo)) {
            throw new AccessDeniedException("Seu perfil não possui permissão para consultar logs deste usuário.");
        }

        if (perfisPermitidos != null && entidadeAlvo != null
                && !perfisPermitidos.contains(entidadeAlvo.getPapel().name())) {
            // Bloqueia acesso direto se não houver nenhum histórico permitido sob o papel anterior do usuário.
            FiltroAuditoriaDTO verificacaoHistorico = new FiltroAuditoriaDTO(
                    usuarioAlvo, null, null, null, null, null, null
            );
            long visiveis = repository.count(criarSpecification(verificacaoHistorico, perfisPermitidos, true));
            if (visiveis == 0) {
                throw new AccessDeniedException("Seu perfil não possui permissão para consultar logs deste usuário.");
            }
        }

        FiltroAuditoriaDTO base = filtro != null ? filtro : new FiltroAuditoriaDTO(null, null, null, null, null, null, null);
        return listarAuditoriaInterno(pageable, base.comUsuario(usuarioAlvo), true);
    }

    /**
     * Retorna métricas e resumo executivo da auditoria geral restrito ao escopo autorizado do usuário.
     */
    @Transactional(readOnly = true)
    public ResumoAuditoriaDTO obterResumo() {
        return obterResumo(null);
    }

    /**
     * Retorna métricas executivas aplicando filtros opcionais e a política de autorização do servidor.
     */
    @Transactional(readOnly = true)
    public ResumoAuditoriaDTO obterResumo(FiltroAuditoriaDTO filtro) {
        validarIntervaloFiltro(filtro);
        List<String> perfisPermitidos = perfisPermitidosDaSessao();
        Specification<AuditoriaGeral> spec = criarSpecification(filtro, perfisPermitidos, false);

        List<AuditoriaGeral> registros = repository.findAll(spec);
        long total = registros.size();
        long sucessos = 0L;
        long ultimas24Horas = 0L;
        OffsetDateTime limite24h = OffsetDateTime.now().minusHours(24);
        Map<String, Long> porAcao = new LinkedHashMap<>();
        Map<String, Long> porEntidade = new LinkedHashMap<>();

        for (AuditoriaGeral a : registros) {
            if (a.isSucesso()) sucessos++;
            if (a.getDataHora() != null && a.getDataHora().isAfter(limite24h)) ultimas24Horas++;
            if (a.getTipoAcao() != null) {
                porAcao.merge(a.getTipoAcao().name(), 1L, Long::sum);
            }
            if (a.getTipoEntidade() != null) {
                porEntidade.merge(a.getTipoEntidade().name(), 1L, Long::sum);
            }
        }
        long falhas = Math.max(0L, total - sucessos);
        return new ResumoAuditoriaDTO(total, sucessos, falhas, ultimas24Horas, porAcao, porEntidade);
    }

    /**
     * Exporta o log de auditoria em formato CSV com UTF-8 BOM, respeitando a autorização do leitor.
     */
    @Transactional(readOnly = true)
    public byte[] exportarCsv(FiltroAuditoriaDTO filtro, String delimitador) {
        validarIntervaloFiltro(filtro);
        String delim = (delimitador != null && !delimitador.isBlank()) ? delimitador : ";";
        List<String> perfisPermitidos = perfisPermitidosDaSessao();
        Specification<AuditoriaGeral> spec = criarSpecification(filtro, perfisPermitidos, false);
        List<AuditoriaGeral> logs = repository.findAll(spec, Sort.by(Sort.Direction.DESC, "dataHora"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
                writer.println(String.join(delim,
                        "Protocolo",
                        "DataHora",
                        "Usuario",
                        "PerfilUsuario",
                        "TipoAcao",
                        "TipoEntidade",
                        "EntidadeId",
                        "Versao",
                        "Descricao",
                        "Sucesso",
                        "IpOrigem",
                        "UserAgent",
                        "CampanhaId",
                        "ReportIdLote",
                        "IdLote",
                        "ProtocoloCalculo",
                        "Competencia"
                ));

                for (AuditoriaGeral a : logs) {
                    writer.println(String.join(delim,
                            a.getProtocolo() != null ? a.getProtocolo().toString() : "",
                            a.getDataHora() != null ? a.getDataHora().format(ISO_FORMATTER) : "",
                            escapeCsv(a.getUsuario()),
                            escapeCsv(a.getPerfilUsuario() != null ? a.getPerfilUsuario() : ""),
                            a.getTipoAcao() != null ? a.getTipoAcao().name() : "",
                            a.getTipoEntidade() != null ? a.getTipoEntidade().name() : "",
                            escapeCsv(a.getEntidadeId() != null ? a.getEntidadeId() : ""),
                            a.getVersaoEntidade() != null ? a.getVersaoEntidade().toString() : "",
                            escapeCsv(a.getDescricao()),
                            a.isSucesso() ? "SIM" : "NAO",
                            escapeCsv(a.getIpOrigem() != null ? a.getIpOrigem() : ""),
                            escapeCsv(a.getUserAgent() != null ? a.getUserAgent() : ""),
                            a.getCampanhaId() != null ? a.getCampanhaId().toString() : "",
                            a.getReportId() != null ? a.getReportId().toString() : "",
                            escapeCsv(a.getIdLote() != null ? a.getIdLote() : ""),
                            a.getProtocoloCalculo() != null ? a.getProtocoloCalculo().toString() : "",
                            escapeCsv(a.getCompetencia() != null ? a.getCompetencia() : "")
                    ));
                }
                writer.flush();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao gerar exportação CSV da auditoria", e);
            throw new BusinessException("Falha ao exportar CSV da auditoria: " + e.getMessage());
        }
    }

    private Specification<AuditoriaGeral> criarSpecification(
            FiltroAuditoriaDTO filtro,
            List<String> perfisPermitidos,
            boolean usuarioExato
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (perfisPermitidos != null) {
                predicates.add(root.get("perfilUsuario").in(perfisPermitidos));
            }

            if (filtro != null) {
                if (filtro.usuario() != null && !filtro.usuario().isBlank()) {
                    String u = filtro.usuario().trim().toLowerCase();
                    predicates.add(usuarioExato
                            ? cb.equal(cb.lower(root.get("usuario")), u)
                            : cb.like(cb.lower(root.get("usuario")), "%" + u + "%"));
                }
                if (filtro.perfilUsuario() != null && !filtro.perfilUsuario().isBlank()) {
                    predicates.add(cb.equal(cb.upper(root.get("perfilUsuario")), filtro.perfilUsuario().trim().toUpperCase()));
                }
                if (filtro.tipoAcao() != null) {
                    predicates.add(cb.equal(root.get("tipoAcao"), filtro.tipoAcao()));
                }
                if (filtro.tipoEntidade() != null) {
                    predicates.add(cb.equal(root.get("tipoEntidade"), filtro.tipoEntidade()));
                }
                if (filtro.entidadeId() != null && !filtro.entidadeId().isBlank()) {
                    predicates.add(cb.equal(root.get("entidadeId"), filtro.entidadeId().trim()));
                }
                if (filtro.campanhaId() != null) {
                    predicates.add(cb.equal(root.get("campanhaId"), filtro.campanhaId()));
                }
                if (filtro.reportId() != null) {
                    predicates.add(cb.equal(root.get("reportId"), filtro.reportId()));
                }
                if (filtro.idLote() != null && !filtro.idLote().isBlank()) {
                    predicates.add(cb.equal(cb.upper(root.get("idLote")), filtro.idLote().trim().toUpperCase()));
                }
                if (filtro.envioArquivoId() != null) {
                    predicates.add(cb.equal(root.get("envioArquivoId"), filtro.envioArquivoId()));
                }
                if (filtro.arquivoImportacaoId() != null) {
                    predicates.add(cb.equal(root.get("arquivoImportacaoId"), filtro.arquivoImportacaoId()));
                }
                if (filtro.protocoloCalculo() != null) {
                    predicates.add(cb.equal(root.get("protocoloCalculo"), filtro.protocoloCalculo()));
                }
                if (filtro.execucaoCalculoId() != null) {
                    predicates.add(cb.equal(root.get("execucaoCalculoId"), filtro.execucaoCalculoId()));
                }
                if (filtro.fechamentoId() != null) {
                    predicates.add(cb.equal(root.get("fechamentoId"), filtro.fechamentoId()));
                }
                if (filtro.competencia() != null && !filtro.competencia().isBlank()) {
                    predicates.add(cb.equal(root.get("competencia"), filtro.competencia().trim()));
                }
                if (filtro.dataInicio() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dataHora"), filtro.dataInicio()));
                }
                if (filtro.dataFim() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dataHora"), filtro.dataFim()));
                }
                if (filtro.sucesso() != null) {
                    predicates.add(cb.equal(root.get("sucesso"), filtro.sucesso()));
                }
                if (filtro.busca() != null && !filtro.busca().isBlank()) {
                    String termo = "%" + filtro.busca().trim().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("usuario")), termo),
                            cb.like(cb.lower(root.get("descricao")), termo),
                            cb.like(cb.lower(cb.coalesce(root.get("entidadeId"), "")), termo),
                            cb.like(cb.lower(cb.coalesce(root.get("campanhaTitulo"), "")), termo),
                            cb.like(cb.lower(cb.coalesce(root.get("nomeArquivo"), "")), termo),
                            cb.like(cb.lower(cb.coalesce(root.get("idLote"), "")), termo)
                    ));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<String> perfisPermitidosDaSessao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (!(principal instanceof Usuario usuario)) {
            return null;
        }
        return PerfilPermissoes.perfisLogsVisiveis(usuario.getPapel());
    }

    private void validarAcessoAoRegistro(AuditoriaGeral audit) {
        List<String> perfis = perfisPermitidosDaSessao();
        if (perfis != null && (audit.getPerfilUsuario() == null || !perfis.contains(audit.getPerfilUsuario()))) {
            throw new AccessDeniedException("Seu perfil não possui permissão para visualizar este evento de auditoria.");
        }
    }

    private Usuario resolverUsuarioAlvo(String usuarioOuId) {
        if (usuarioRepository == null) return null;
        try {
            UUID id = UUID.fromString(usuarioOuId);
            return usuarioRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com ID: " + usuarioOuId));
        } catch (IllegalArgumentException ignored) {
            return usuarioRepository.findByEmailIgnoreCase(usuarioOuId).orElse(null);
        }
    }

    private String resolverUsuarioEfetivo(String candidato) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if ((candidato == null || candidato.isBlank() || "SISTEMA".equalsIgnoreCase(candidato.trim()))
                && principal instanceof Usuario u && u.getEmail() != null && !u.getEmail().isBlank()) {
            return u.getEmail().trim();
        }
        return (candidato != null && !candidato.isBlank()) ? candidato.trim() : "SISTEMA";
    }

    private String resolverPerfilEfetivo(String perfilInformado, String usuarioEfetivo) {
        if (perfilInformado != null && !perfilInformado.isBlank()) {
            return perfilInformado.trim();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (principal instanceof Usuario u && u.getPapel() != null) {
            if ("SISTEMA".equalsIgnoreCase(usuarioEfetivo)
                    || (u.getEmail() != null && u.getEmail().equalsIgnoreCase(usuarioEfetivo))) {
                return u.getPapel().name();
            }
        }
        if (usuarioRepository != null && usuarioEfetivo != null && !"SISTEMA".equalsIgnoreCase(usuarioEfetivo)) {
            Optional<Usuario> encontrado = usuarioRepository.findByEmailIgnoreCase(usuarioEfetivo);
            if (encontrado.isPresent() && encontrado.get().getPapel() != null) {
                return encontrado.get().getPapel().name();
            }
        }
        return "SISTEMA";
    }

    private void inferirReferenciaPorEntidade(AuditoriaEventoContexto ctx) {
        if (ctx.getTipoEntidade() == null || ctx.getEntidadeId() == null || ctx.getEntidadeId().isBlank()) {
            return;
        }
        String ent = ctx.getEntidadeId().trim();
        switch (ctx.getTipoEntidade()) {
            case CAMPANHA, CAMPANHA_VERSAO, ESTEIRA, PROGRAMA_PYTHON, PENDENCIA -> {
                if (ctx.getCampanhaId() == null) {
                    try { ctx.campanhaId(Long.parseLong(ent)); } catch (NumberFormatException ignored) {}
                }
            }
            case SIMULACAO -> {
                if (ctx.getCampanhaId() == null) {
                    try { ctx.campanhaId(Long.parseLong(ent)); } catch (NumberFormatException ignored) {}
                }
                if (ctx.getSimulacaoId() == null) {
                    try { ctx.simulacaoId(UUID.fromString(ent)); } catch (IllegalArgumentException ignored) {}
                }
            }
            case APURACAO -> {
                if (ctx.getCampanhaId() == null) {
                    try { ctx.campanhaId(Long.parseLong(ent)); } catch (NumberFormatException ignored) {}
                }
                if (ctx.getApuracaoCampanhaId() == null) {
                    try { ctx.apuracaoCampanhaId(UUID.fromString(ent)); } catch (IllegalArgumentException ignored) {}
                }
            }
            case EXECUCAO_CALCULO -> {
                if (ctx.getExecucaoCalculoId() == null) {
                    try { ctx.execucaoCalculoId(UUID.fromString(ent)); } catch (IllegalArgumentException ignored) {}
                }
            }
            case CALCULO -> {
                if (ctx.getLogCalculoId() == null && ctx.getProtocoloCalculo() == null) {
                    try { ctx.protocoloCalculo(UUID.fromString(ent)); } catch (IllegalArgumentException ignored) {}
                }
            }
            case FECHAMENTO -> {
                if (ctx.getFechamentoId() == null) {
                    try { ctx.fechamentoId(UUID.fromString(ent)); } catch (IllegalArgumentException ignored) {}
                }
                if (ctx.getCompetencia() == null && ent.matches("\\d{4}-\\d{2}")) {
                    ctx.competencia(ent);
                }
            }
            case LOTE_IMPORTACAO, BASE_CADASTRO -> {
                if (ctx.getReportId() == null) {
                    try { ctx.reportId(Long.parseLong(ent)); } catch (NumberFormatException ignored) {}
                }
                if (ctx.getIdLote() == null && ent.length() >= 8) {
                    try {
                        UUID.fromString(ent);
                        ctx.idLote(ent);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            case LOTE_PREPARACAO -> {
                if (ctx.getLotePreparacaoId() == null) {
                    try { ctx.lotePreparacaoId(UUID.fromString(ent)); } catch (IllegalArgumentException ignored) {}
                }
            }
            default -> { }
        }
    }

    private void preencherVinculos(AuditoriaGeral audit, AuditoriaEventoContexto ctx) {
        audit.setCampanhaId(ctx.getCampanhaId());
        audit.setCampanhaVersaoId(ctx.getCampanhaVersaoId());
        audit.setCampanhaTitulo(ctx.getCampanhaTitulo());
        audit.setEnvioArquivoId(ctx.getEnvioArquivoId());
        audit.setArquivoImportacaoId(ctx.getArquivoImportacaoId());
        audit.setLotePreparacaoId(ctx.getLotePreparacaoId());
        audit.setIdLote(ctx.getIdLote());
        audit.setReportId(ctx.getReportId());
        audit.setNomeArquivo(ctx.getNomeArquivo());
        audit.setSimulacaoId(ctx.getSimulacaoId());
        audit.setApuracaoCampanhaId(ctx.getApuracaoCampanhaId());
        audit.setExecucaoCalculoId(ctx.getExecucaoCalculoId());
        audit.setFechamentoId(ctx.getFechamentoId());
        audit.setProtocoloCalculo(ctx.getProtocoloCalculo());
        audit.setLogCalculoId(ctx.getLogCalculoId());
        audit.setCompetencia(ctx.getCompetencia());
    }

    private List<AuditoriaResponseDTO> hidratarRegistros(List<AuditoriaGeral> registros) {
        if (registros == null || registros.isEmpty()) {
            return List.of();
        }

        Set<Long> campanhaIds = new HashSet<>();
        Set<Long> envioIds = new HashSet<>();
        Set<Long> reportIds = new HashSet<>();
        Set<String> idLotes = new HashSet<>();
        Set<UUID> execucaoIds = new HashSet<>();
        Set<UUID> apuracaoIds = new HashSet<>();
        Set<UUID> fechamentoIds = new HashSet<>();
        Set<UUID> simulacaoIds = new HashSet<>();

        for (AuditoriaGeral a : registros) {
            if (a.getCampanhaId() != null) campanhaIds.add(a.getCampanhaId());
            if (a.getEnvioArquivoId() != null) envioIds.add(a.getEnvioArquivoId());
            if (a.getReportId() != null) reportIds.add(a.getReportId());
            if (a.getIdLote() != null && !a.getIdLote().isBlank()) idLotes.add(a.getIdLote());
            if (a.getExecucaoCalculoId() != null) execucaoIds.add(a.getExecucaoCalculoId());
            if (a.getApuracaoCampanhaId() != null) apuracaoIds.add(a.getApuracaoCampanhaId());
            if (a.getFechamentoId() != null) fechamentoIds.add(a.getFechamentoId());
            if (a.getSimulacaoId() != null) simulacaoIds.add(a.getSimulacaoId());
        }

        Map<Long, Campanha> campanhas = new HashMap<>();
        if (campanhaRepository != null && !campanhaIds.isEmpty()) {
            campanhaRepository.findAllById(campanhaIds).forEach(c -> campanhas.put(c.getId(), c));
        }

        Map<Long, EnvioArquivo> enviosPorId = new HashMap<>();
        Map<Long, EnvioArquivo> enviosPorReport = new HashMap<>();
        Map<String, EnvioArquivo> enviosPorLote = new HashMap<>();
        if (envioArquivoRepository != null) {
            if (!envioIds.isEmpty()) {
                envioArquivoRepository.findByIdIn(envioIds).forEach(e -> registrarEnvioMap(e, enviosPorId, enviosPorReport, enviosPorLote));
            }
            if (!reportIds.isEmpty()) {
                envioArquivoRepository.findByReportIdIn(reportIds).forEach(e -> registrarEnvioMap(e, enviosPorId, enviosPorReport, enviosPorLote));
            }
            if (!idLotes.isEmpty()) {
                envioArquivoRepository.findByIdLoteIn(idLotes).forEach(e -> registrarEnvioMap(e, enviosPorId, enviosPorReport, enviosPorLote));
            }
        }

        Map<UUID, ExecucaoCalculo> execucoes = new HashMap<>();
        if (execucaoCalculoRepository != null && !execucaoIds.isEmpty()) {
            execucaoCalculoRepository.findAllById(execucaoIds).forEach(e -> execucoes.put(e.getId(), e));
        }

        Map<UUID, CampanhaApuracao> apuracoes = new HashMap<>();
        if (campanhaApuracaoRepository != null && !apuracaoIds.isEmpty()) {
            campanhaApuracaoRepository.findAllById(apuracaoIds).forEach(ap -> apuracoes.put(ap.getId(), ap));
        }

        Map<UUID, FechamentoCompetencia> fechamentos = new HashMap<>();
        if (fechamentoCompetenciaRepository != null && !fechamentoIds.isEmpty()) {
            fechamentoCompetenciaRepository.findAllById(fechamentoIds).forEach(f -> fechamentos.put(f.getId(), f));
        }

        Map<UUID, Simulacao> simulacoes = new HashMap<>();
        if (simulacaoRepository != null && !simulacaoIds.isEmpty()) {
            simulacaoRepository.findAllById(simulacaoIds).forEach(s -> simulacoes.put(s.getId(), s));
        }

        List<AuditoriaResponseDTO> resultado = new ArrayList<>(registros.size());
        for (AuditoriaGeral a : registros) {
            RelacionamentoCampanhaResumoDTO relCampanha = resolverRelacionamentoCampanha(a, campanhas);
            RelacionamentoArquivoResumoDTO relArquivo = resolverRelacionamentoArquivo(a, enviosPorId, enviosPorReport, enviosPorLote);
            RelacionamentoCalculoResumoDTO relCalculo = resolverRelacionamentoCalculo(a, execucoes, apuracoes, fechamentos, simulacoes);
            AuditoriaRelacionamentosDTO rel = new AuditoriaRelacionamentosDTO(relCampanha, relArquivo, relCalculo);
            resultado.add(AuditoriaResponseDTO.de(a, rel));
        }
        return resultado;
    }

    private void registrarEnvioMap(
            EnvioArquivo e,
            Map<Long, EnvioArquivo> porId,
            Map<Long, EnvioArquivo> porReport,
            Map<String, EnvioArquivo> porLote
    ) {
        if (e.getId() != null) porId.putIfAbsent(e.getId(), e);
        if (e.getReportId() != null) porReport.putIfAbsent(e.getReportId(), e);
        if (e.getIdLote() != null) porLote.putIfAbsent(e.getIdLote(), e);
    }

    private RelacionamentoCampanhaResumoDTO resolverRelacionamentoCampanha(AuditoriaGeral a, Map<Long, Campanha> campanhas) {
        if (a.getCampanhaId() == null && a.getCampanhaVersaoId() == null && a.getCampanhaTitulo() == null) {
            return null;
        }
        Campanha c = a.getCampanhaId() != null ? campanhas.get(a.getCampanhaId()) : null;
        CampanhaVersao versao = null;
        if (c != null) {
            if (a.getCampanhaVersaoId() != null) {
                if (c.getVersaoProposta() != null && Objects.equals(c.getVersaoProposta().getId(), a.getCampanhaVersaoId())) {
                    versao = c.getVersaoProposta();
                } else if (c.getVersaoVigente() != null && Objects.equals(c.getVersaoVigente().getId(), a.getCampanhaVersaoId())) {
                    versao = c.getVersaoVigente();
                }
            }
            if (versao == null) {
                versao = c.versaoParaRevisao();
            }
        }
        return new RelacionamentoCampanhaResumoDTO(
                a.getCampanhaId(),
                c != null ? c.getTitulo() : a.getCampanhaTitulo(),
                c != null && c.getEstado() != null ? c.getEstado().name() : null,
                a.getCampanhaVersaoId() != null ? a.getCampanhaVersaoId() : (versao != null ? versao.getId() : null),
                a.getVersaoEntidade() != null ? a.getVersaoEntidade() : (versao != null ? versao.getNumero() : null),
                versao != null && versao.getStatus() != null ? versao.getStatus().name() : null,
                versao != null && versao.getEstadoEsteira() != null ? versao.getEstadoEsteira().name() : null
        );
    }

    private RelacionamentoArquivoResumoDTO resolverRelacionamentoArquivo(
            AuditoriaGeral a,
            Map<Long, EnvioArquivo> porId,
            Map<Long, EnvioArquivo> porReport,
            Map<String, EnvioArquivo> porLote
    ) {
        EnvioArquivo e = a.getEnvioArquivoId() != null ? porId.get(a.getEnvioArquivoId()) : null;
        if (e == null && a.getReportId() != null) e = porReport.get(a.getReportId());
        if (e == null && a.getIdLote() != null) e = porLote.get(a.getIdLote());

        if (e == null && a.getEnvioArquivoId() == null && a.getReportId() == null
                && a.getIdLote() == null && a.getArquivoImportacaoId() == null && a.getLotePreparacaoId() == null) {
            return null;
        }

        return new RelacionamentoArquivoResumoDTO(
                e != null ? e.getId() : a.getEnvioArquivoId(),
                e != null ? e.getReportId() : a.getReportId(),
                e != null ? e.getIdLote() : a.getIdLote(),
                e != null && e.getArquivoImportacaoId() != null ? e.getArquivoImportacaoId() : a.getArquivoImportacaoId(),
                a.getLotePreparacaoId(),
                e != null ? e.getNomeArquivo() : a.getNomeArquivo(),
                e != null ? e.getTipoBase() : null,
                e != null && e.getCompetencia() != null ? e.getCompetencia() : a.getCompetencia(),
                e != null ? e.getStatus() : null,
                e != null ? e.getTotalLinhas() : null,
                e != null ? e.getLinhasValidas() : null,
                e != null ? e.getCriadoPor() : null,
                e != null ? "EXCLUIDO".equalsIgnoreCase(e.getStatus()) : null
        );
    }

    private RelacionamentoCalculoResumoDTO resolverRelacionamentoCalculo(
            AuditoriaGeral a,
            Map<UUID, ExecucaoCalculo> execucoes,
            Map<UUID, CampanhaApuracao> apuracoes,
            Map<UUID, FechamentoCompetencia> fechamentos,
            Map<UUID, Simulacao> simulacoes
    ) {
        if (a.getProtocoloCalculo() == null && a.getLogCalculoId() == null
                && a.getExecucaoCalculoId() == null && a.getApuracaoCampanhaId() == null
                && a.getFechamentoId() == null && a.getSimulacaoId() == null) {
            return null;
        }
        ExecucaoCalculo ex = a.getExecucaoCalculoId() != null ? execucoes.get(a.getExecucaoCalculoId()) : null;
        CampanhaApuracao ap = a.getApuracaoCampanhaId() != null ? apuracoes.get(a.getApuracaoCampanhaId()) : null;
        FechamentoCompetencia fc = a.getFechamentoId() != null ? fechamentos.get(a.getFechamentoId()) : null;
        Simulacao sim = a.getSimulacaoId() != null ? simulacoes.get(a.getSimulacaoId()) : null;

        String comp = a.getCompetencia();
        if (comp == null && ex != null) comp = ex.getCompetencia();
        if (comp == null && ap != null) comp = ap.getCompetencia();
        if (comp == null && fc != null) comp = fc.getCompetencia();
        if (comp == null && sim != null) comp = sim.getCompetencia();

        String status = null;
        Integer totalItens = null;
        Integer totalImpedimentos = null;
        BigDecimal valorTotalVendas = null;
        BigDecimal valorTotalComissao = null;
        UUID protocoloEfetivo = a.getProtocoloCalculo();

        if (fc != null) {
            status = fc.getStatus() != null ? fc.getStatus().name() : null;
            totalItens = fc.getTotalVendasProcessadas();
            totalImpedimentos = fc.getTotalImpedimentos();
            valorTotalVendas = fc.getValorTotalVendas();
            valorTotalComissao = fc.getValorTotalComissao();
            if (protocoloEfetivo == null) protocoloEfetivo = fc.getProtocolo();
        } else if (ap != null) {
            status = ap.getStatus();
            totalItens = ap.getTotalVendasProcessadas();
            totalImpedimentos = ap.getTotalImpedimentos();
            valorTotalVendas = ap.getValorTotalVendas();
            valorTotalComissao = ap.getValorTotalComissao();
            if (protocoloEfetivo == null) protocoloEfetivo = ap.getProtocolo();
        } else if (ex != null) {
            status = ex.getStatus() != null ? ex.getStatus().name() : null;
            totalItens = ex.getTotalItens();
            valorTotalComissao = ex.getComissaoTotalApurada();
        } else if (sim != null) {
            status = sim.getStatusAceite() != null ? sim.getStatusAceite().name() : null;
            totalItens = sim.getTotalVendasRecortadas();
            totalImpedimentos = sim.getQtdVendasComImpedimento();
            valorTotalVendas = sim.getValorTotalRecortado();
            valorTotalComissao = sim.getComissaoProjetada();
            if (protocoloEfetivo == null) protocoloEfetivo = sim.getProtocolo();
        }

        return new RelacionamentoCalculoResumoDTO(
                protocoloEfetivo,
                a.getLogCalculoId(),
                a.getExecucaoCalculoId(),
                a.getApuracaoCampanhaId(),
                a.getFechamentoId(),
                a.getSimulacaoId(),
                comp,
                status,
                ex != null ? "ASYNC_COMPETENCIA" : (ap != null ? "APURACAO_DEFINITIVA" : (fc != null ? "FECHAMENTO" : null)),
                totalItens,
                totalImpedimentos,
                valorTotalVendas,
                valorTotalComissao
        );
    }

    private String obterIpHttpAtual() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            return req != null ? req.getRemoteAddr() : null;
        }
        return null;
    }

    private String obterUserAgentHttpAtual() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            return req != null ? req.getHeader("User-Agent") : null;
        }
        return null;
    }

    private static void validarIntervaloFiltro(FiltroAuditoriaDTO filtro) {
        if (filtro != null && filtro.dataInicio() != null && filtro.dataFim() != null
                && filtro.dataInicio().isAfter(filtro.dataFim())) {
            throw new BusinessException("Data de início não pode ser posterior à data de término.");
        }
    }

    private static TipoAcaoAuditoria tipoAcaoNula() {
        return null;
    }

    private static boolean containsIgnoreCase(String value, String termoLower) {
        return value != null && value.toLowerCase().contains(termoLower);
    }

    private static String resolverNomePerfil(String papel) {
        if (papel == null || papel.isBlank()) return "Sistema";
        try {
            return PapelUsuario.valueOf(papel).getNome();
        } catch (IllegalArgumentException ex) {
            return papel;
        }
    }

    private String escapeCsv(String valor) {
        if (valor == null) return "";
        if (valor.contains(";") || valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    private static class UsuarioAgregado {
        final String usuario;
        UUID usuarioId;
        String nome;
        String email;
        String papel;
        String nomePerfil;
        Boolean ativo;
        long totalEventosAuditoria;
        long totalLogsCalculo;
        long totalSucessos;
        long totalFalhas;
        OffsetDateTime ultimaAtividadeEm;

        UsuarioAgregado(String usuario) {
            this.usuario = usuario;
            this.email = usuario;
        }

        AuditoriaUsuarioResumoDTO toDto() {
            return new AuditoriaUsuarioResumoDTO(
                    usuario,
                    usuarioId,
                    nome,
                    email,
                    papel,
                    nomePerfil,
                    ativo,
                    totalEventosAuditoria,
                    totalLogsCalculo,
                    totalSucessos,
                    totalFalhas,
                    ultimaAtividadeEm
            );
        }
    }
}
