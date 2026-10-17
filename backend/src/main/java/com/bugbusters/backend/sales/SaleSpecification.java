package com.bugbusters.backend.sales;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

/** Filtros combináveis de vendas para consultas somente leitura (ex.: dados de simulação). */
public final class SaleSpecification {

    private SaleSpecification() {}

    public static Specification<Sale> comFiltros(LocalDate inicio, LocalDate fim, Integer codMarca,
                                                  Integer codLoja, Integer codCargo, String matricula) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.between(root.get("saleDate"), inicio, fim));
            if (codMarca != null) {
                predicados.add(cb.equal(root.get("brand").get("code"), codMarca));
            }
            if (codLoja != null) {
                predicados.add(cb.equal(root.get("store").get("code"), codLoja));
            }
            if (codCargo != null) {
                predicados.add(cb.equal(root.get("registration").get("position").get("code"), codCargo));
            }
            if (matricula != null && !matricula.isBlank()) {
                predicados.add(cb.equal(root.get("registration").get("registration"), matricula));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
