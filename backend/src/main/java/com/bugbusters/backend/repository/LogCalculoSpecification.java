package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.LogCalculoImutavel;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Especificação JPA dinâmica para filtros combináveis de logs imutáveis de cálculo.
 * Suporta filtros por venda (idVenda ou matrícula), regra (idRegra), período (dataVenda),
 * tipo de venda (informada vs importada), identificador de lote de origem, usuário,
 * perfil autorizador, protocolo e campanha (BUG-23, BUG-24 e BACK-17).
 */
public class LogCalculoSpecification {

    private LogCalculoSpecification() {
        // Construtor privado para classe utilitária
    }

    public static Specification<LogCalculoImutavel> comFiltros(
            UUID idVenda,
            String matricula,
            Long idRegra,
            LocalDate dataInicio,
            LocalDate dataFim,
            String tipoVenda,
            String idLoteOrigem
    ) {
        return comFiltrosAvancados(idVenda, matricula, idRegra, dataInicio, dataFim, tipoVenda,
                idLoteOrigem, null, null, null, null, null, null);
    }

    public static Specification<LogCalculoImutavel> comFiltrosAvancados(
            UUID idVenda,
            String matricula,
            Long idRegra,
            LocalDate dataInicio,
            LocalDate dataFim,
            String tipoVenda,
            String idLoteOrigem,
            String usuarioExecutor,
            String perfilExecutorFiltro,
            UUID protocolo,
            Collection<Long> regraIdsCampanha,
            OffsetDateTime execucaoInicio,
            OffsetDateTime execucaoFim
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (idVenda != null) {
                predicates.add(cb.equal(root.get("idVenda"), idVenda));
            }

            if (matricula != null && !matricula.isBlank()) {
                predicates.add(cb.equal(
                        cb.upper(root.get("matricula")),
                        matricula.trim().toUpperCase()
                ));
            }

            if (idRegra != null) {
                predicates.add(cb.equal(root.get("idRegra"), idRegra));
            }

            if (regraIdsCampanha != null) {
                if (regraIdsCampanha.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("idRegra").in(regraIdsCampanha));
                }
            }

            if (dataInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataVenda"), dataInicio));
            }

            if (dataFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataVenda"), dataFim));
            }

            if (execucaoInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("executadoEm"), execucaoInicio));
            }

            if (execucaoFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("executadoEm"), execucaoFim));
            }

            if (tipoVenda != null && !tipoVenda.isBlank()) {
                predicates.add(cb.equal(
                        cb.upper(root.get("tipoVenda")),
                        tipoVenda.trim().toUpperCase()
                ));
            }

            if (idLoteOrigem != null && !idLoteOrigem.isBlank()) {
                predicates.add(cb.equal(
                        cb.upper(root.get("idLoteOrigem")),
                        idLoteOrigem.trim().toUpperCase()
                ));
            }

            if (usuarioExecutor != null && !usuarioExecutor.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("usuarioExecutor")),
                        "%" + usuarioExecutor.trim().toLowerCase() + "%"
                ));
            }

            if (perfilExecutorFiltro != null && !perfilExecutorFiltro.isBlank()) {
                predicates.add(cb.equal(
                        cb.upper(root.get("perfilExecutor")),
                        perfilExecutorFiltro.trim().toUpperCase()
                ));
            }

            if (protocolo != null) {
                predicates.add(cb.equal(root.get("protocolo"), protocolo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<LogCalculoImutavel> usuarioExato(String email) {
        return (root, query, cb) -> email == null || email.isBlank()
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("usuarioExecutor")), email.trim().toLowerCase());
    }

    public static Specification<LogCalculoImutavel> perfisExecutores(Collection<String> perfis) {
        return (root, query, cb) -> perfis == null ? cb.conjunction() : root.get("perfilExecutor").in(perfis);
    }

    public static Specification<LogCalculoImutavel> comFiltros(
            UUID idVenda,
            String matricula,
            Long idRegra,
            LocalDate dataInicio,
            LocalDate dataFim
    ) {
        return comFiltros(idVenda, matricula, idRegra, dataInicio, dataFim, null, null);
    }
}
