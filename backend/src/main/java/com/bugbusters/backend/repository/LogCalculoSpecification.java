package com.bugbusters.backend.repository;

import com.bugbusters.backend.model.LogCalculoImutavel;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Especificação JPA dinâmica para filtros combináveis de logs imutáveis de cálculo.
 * Suporta filtros por venda (idVenda ou matrícula), regra (idRegra) e período (dataVenda).
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
            LocalDate dataFim
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

            if (dataInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dataVenda"), dataInicio));
            }

            if (dataFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dataVenda"), dataFim));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
