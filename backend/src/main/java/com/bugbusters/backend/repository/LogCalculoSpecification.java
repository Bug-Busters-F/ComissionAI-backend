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
 * Suporta filtros por venda (idVenda ou matrícula), regra (idRegra), período (dataVenda),
 * tipo de venda (informada vs importada) e identificador de lote de origem (BUG-23 e BUG-24).
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
