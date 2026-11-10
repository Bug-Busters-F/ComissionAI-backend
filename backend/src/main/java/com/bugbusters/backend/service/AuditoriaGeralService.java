package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.auditoria.AuditoriaResponseDTO;
import com.bugbusters.backend.dto.auditoria.FiltroAuditoriaDTO;
import com.bugbusters.backend.dto.auditoria.RegistroAuditoriaDTO;
import com.bugbusters.backend.dto.auditoria.ResumoAuditoriaDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.AuditoriaGeral;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import com.bugbusters.backend.repository.AuditoriaGeralRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável pelo registro e consulta da trilha de auditoria geral (BUG-111 / BACK-16).
 */
@Service
public class AuditoriaGeralService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaGeralService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AuditoriaGeralRepository repository;

    @Autowired
    public AuditoriaGeralService(AuditoriaGeralRepository repository) {
        this.repository = repository;
    }

    /**
     * Registra formalmente uma ação auditável no banco de dados.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditoriaResponseDTO registrarAcao(RegistroAuditoriaDTO dto) {
        if (dto == null) {
            throw new BusinessException("Dados de auditoria não podem ser nulos.");
        }

        AuditoriaGeral audit = new AuditoriaGeral(
                dto.usuario(),
                dto.perfilUsuario(),
                dto.tipoAcao(),
                dto.tipoEntidade(),
                dto.entidadeId(),
                dto.versaoEntidade(),
                dto.descricao(),
                dto.dadosAnteriores(),
                dto.dadosNovos(),
                dto.ipOrigem(),
                dto.userAgent(),
                dto.sucesso() != null ? dto.sucesso() : true,
                dto.detalheErro()
        );

        AuditoriaGeral salvo = repository.save(audit);
        log.info("Auditoria registrada: [Protocolo: {}] [{}] [{}] por {}",
                salvo.getProtocolo(), salvo.getTipoAcao(), salvo.getTipoEntidade(), salvo.getUsuario());

        return AuditoriaResponseDTO.de(salvo);
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
        return registrarAcao(new RegistroAuditoriaDTO(
                usuario, null, tipoAcao, tipoEntidade, entidadeId, versaoEntidade,
                descricao, null, null, null, null, true, null
        ));
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
        return registrarAcao(new RegistroAuditoriaDTO(
                usuario, null, tipoAcao, tipoEntidade, entidadeId, versaoEntidade,
                descricao, null, dadosNovos, null, null, true, null
        ));
    }

    /**
     * Consulta paginada com filtros dinâmicos.
     */
    @Transactional(readOnly = true)
    public Page<AuditoriaResponseDTO> listarAuditoria(Pageable pageable, FiltroAuditoriaDTO filtro) {
        Specification<AuditoriaGeral> spec = criarSpecification(filtro);
        return repository.findAll(spec, pageable).map(AuditoriaResponseDTO::de);
    }

    /**
     * Busca um registro de auditoria pelo protocolo único.
     */
    @Transactional(readOnly = true)
    public AuditoriaResponseDTO buscarPorProtocolo(UUID protocolo) {
        return repository.findByProtocolo(protocolo)
                .map(AuditoriaResponseDTO::de)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de auditoria não encontrado para o protocolo: " + protocolo));
    }

    /**
     * Lista a trilha histórica de auditoria de uma entidade específica.
     */
    @Transactional(readOnly = true)
    public Page<AuditoriaResponseDTO> listarPorEntidade(
            TipoEntidadeAuditoria tipoEntidade,
            String entidadeId,
            Pageable pageable
    ) {
        return repository.findAllByTipoEntidadeAndEntidadeIdOrderByDataHoraDesc(tipoEntidade, entidadeId, pageable)
                .map(AuditoriaResponseDTO::de);
    }

    /**
     * Retorna métricas e resumo executivo da auditoria geral.
     */
    @Transactional(readOnly = true)
    public ResumoAuditoriaDTO obterResumo() {
        List<AuditoriaGeral> todos = repository.findAll();

        long total = todos.size();
        long sucessos = todos.stream().filter(AuditoriaGeral::isSucesso).count();
        long falhas = total - sucessos;

        OffsetDateTime umDiaAtras = OffsetDateTime.now().minusHours(24);
        long ultimas24Horas = todos.stream().filter(a -> a.getDataHora().isAfter(umDiaAtras)).count();

        Map<String, Long> porAcao = todos.stream()
                .collect(Collectors.groupingBy(a -> a.getTipoAcao().name(), Collectors.counting()));

        Map<String, Long> porEntidade = todos.stream()
                .collect(Collectors.groupingBy(a -> a.getTipoEntidade().name(), Collectors.counting()));

        return new ResumoAuditoriaDTO(total, sucessos, falhas, ultimas24Horas, porAcao, porEntidade);
    }

    /**
     * Exporta o log de auditoria em formato CSV com UTF-8 BOM e delimitador customizável.
     */
    @Transactional(readOnly = true)
    public byte[] exportarCsv(FiltroAuditoriaDTO filtro, String delimitador) {
        String delim = (delimitador != null && !delimitador.isBlank()) ? delimitador : ";";
        Specification<AuditoriaGeral> spec = criarSpecification(filtro);
        List<AuditoriaGeral> logs = repository.findAll(spec, Sort.by(Sort.Direction.DESC, "dataHora"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // UTF-8 BOM para compatibilidade com Microsoft Excel
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
                        "UserAgent"
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
                            escapeCsv(a.getUserAgent() != null ? a.getUserAgent() : "")
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

    private Specification<AuditoriaGeral> criarSpecification(FiltroAuditoriaDTO filtro) {
        return (root, query, cb) -> {
            if (filtro == null) return cb.conjunction();

            List<Predicate> predicates = new ArrayList<>();

            if (filtro.usuario() != null && !filtro.usuario().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("usuario")), "%" + filtro.usuario().trim().toLowerCase() + "%"));
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
            if (filtro.dataInicio() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataHora"), filtro.dataInicio()));
            }
            if (filtro.dataFim() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataHora"), filtro.dataFim()));
            }
            if (filtro.sucesso() != null) {
                predicates.add(cb.equal(root.get("sucesso"), filtro.sucesso()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String escapeCsv(String valor) {
        if (valor == null) return "";
        if (valor.contains(";") || valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }
}

