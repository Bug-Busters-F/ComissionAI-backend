package com.bugbusters.backend.repository;

import com.bugbusters.backend.dto.relatorio.ItemRelatorioFechamentoDTO;
import com.bugbusters.backend.dto.relatorio.TotalPorColaboradorDTO;
import com.bugbusters.backend.dto.relatorio.TotalPorRegraDTO;
import com.bugbusters.backend.model.ImpedimentoFechamento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Repositório de consultas SQL/JPQL agregadas (GROUP BY) para relatórios consolidados
 * e listagem paginada/filtrada de impedimentos de fechamento (FRONT-11 / BACK-15).
 */
@Repository
public class RelatorioFechamentoQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public record TotaisGeraisRow(
            int totalCalculados,
            BigDecimal valorTotalVendas,
            BigDecimal valorTotalComissao,
            BigDecimal valorTotalBonusPercentual,
            BigDecimal valorTotalBonusFixo,
            int totalColaboradoresDistintos,
            int totalLojasDistintas
    ) {}

    public record CanalGrupoRow(
            String canal,
            int quantidadeVendas,
            int quantidadeColaboradores,
            BigDecimal valorTotalVendas,
            BigDecimal valorTotalComissao,
            BigDecimal valorTotalBonus
    ) {}

    public record EquipeGrupoRow(
            Integer codLoja,
            String nomeLoja,
            Integer codMarca,
            String nomeMarca,
            int quantidadeVendas,
            int quantidadeColaboradores,
            BigDecimal valorTotalVendas,
            BigDecimal valorTotalComissao,
            BigDecimal valorTotalBonus
    ) {}

    public record ColaboradorGranularRow(
            String matricula,
            Integer codCargo,
            String nomeCargo,
            Integer codLoja,
            String nomeLoja,
            Integer codMarca,
            String nomeMarca,
            int quantidadeVendas,
            BigDecimal valorTotalVendas,
            BigDecimal valorTotalComissao,
            BigDecimal valorTotalBonusPercentual,
            BigDecimal valorTotalBonusFixo
    ) {}

    public record ColaboradorResumoRow(
            String matricula,
            String nomeCargo,
            String nomeLoja,
            long quantidadeVendas,
            BigDecimal valorTotalVendas,
            BigDecimal valorTotalComissao
    ) {}

    public TotaisGeraisRow consultarTotaisGerais(
            UUID fechamentoId,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        StringBuilder jpql = new StringBuilder("""
                SELECT COUNT(r.id),
                       SUM(r.valorVenda),
                       SUM(r.valorComissao),
                       SUM(r.valorBonusPercentual),
                       SUM(r.valorBonusFixo),
                       COUNT(DISTINCT r.matricula),
                       COUNT(DISTINCT r.codLoja)
                FROM ResultadoCalculo r
                """);
        appendFiltrosResultado(jpql, "r.fechamentoId", codLoja, codMarca, idRegra, matricula);

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        aplicarParametrosResultado(query, fechamentoId, codLoja, codMarca, idRegra, matricula);
        Object[] row = query.getSingleResult();
        return new TotaisGeraisRow(
                toInt(row[0]),
                toMoney(row[1]),
                toMoney(row[2]),
                toMoney(row[3]),
                toMoney(row[4]),
                toInt(row[5]),
                toInt(row[6])
        );
    }

    public List<TotalPorRegraDTO> agruparPorRegra(
            UUID fechamentoId,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        StringBuilder jpql = new StringBuilder("""
                SELECT r.regraId,
                       reg.nome,
                       reg.tipoOperacao,
                       r.origemTaxa,
                       COUNT(r.id),
                       SUM(r.valorVenda),
                       SUM(r.valorComissao)
                FROM ResultadoCalculo r
                LEFT JOIN Regra reg ON reg.id = r.regraId
                """);
        appendFiltrosResultado(jpql, "r.fechamentoId", codLoja, codMarca, idRegra, matricula);
        jpql.append(" GROUP BY r.regraId, reg.nome, reg.tipoOperacao, r.origemTaxa ");
        jpql.append(" ORDER BY SUM(r.valorComissao) DESC, r.regraId ASC");

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        aplicarParametrosResultado(query, fechamentoId, codLoja, codMarca, idRegra, matricula);

        List<TotalPorRegraDTO> lista = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            Long regraId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String nomeRegra = row[1] != null && !row[1].toString().isBlank()
                    ? row[1].toString()
                    : (regraId != null && regraId == 0L ? "Taxa Base Contratual (BASE_COMISS)"
                    : (regraId != null ? "Regra #" + regraId : "Regra Padrão"));
            String tipoOperacao = row[2] != null
                    ? row[2].toString()
                    : (row[3] != null && !row[3].toString().isBlank() ? row[3].toString() : "DEFINIR_TAXA");
            lista.add(new TotalPorRegraDTO(
                    regraId,
                    nomeRegra,
                    tipoOperacao,
                    toLong(row[4]),
                    toMoney(row[5]),
                    toMoney(row[6])
            ));
        }
        return lista;
    }

    public List<CanalGrupoRow> agruparPorCanal(
            UUID fechamentoId,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        StringBuilder jpql = new StringBuilder("""
                SELECT s.saleChannel,
                       COUNT(r.id),
                       COUNT(DISTINCT r.matricula),
                       SUM(r.valorVenda),
                       SUM(r.valorComissao),
                       SUM(r.valorBonusPercentual),
                       SUM(r.valorBonusFixo)
                FROM ResultadoCalculo r
                LEFT JOIN Sale s ON s.id = r.idVenda
                """);
        appendFiltrosResultado(jpql, "r.fechamentoId", codLoja, codMarca, idRegra, matricula);
        jpql.append(" GROUP BY s.saleChannel ORDER BY SUM(r.valorComissao) DESC");

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        aplicarParametrosResultado(query, fechamentoId, codLoja, codMarca, idRegra, matricula);

        Map<String, CanalGrupoRow> merged = new LinkedHashMap<>();
        for (Object[] row : query.getResultList()) {
            String rawCanal = row[0] != null ? row[0].toString().trim() : "";
            String canal = rawCanal.isBlank() ? "PADRAO" : rawCanal;
            int qtdVendas = toInt(row[1]);
            int qtdColab = toInt(row[2]);
            BigDecimal vendas = toMoney(row[3]);
            BigDecimal comissao = toMoney(row[4]);
            BigDecimal bonus = toMoney(row[5]).add(toMoney(row[6])).setScale(2, RoundingMode.HALF_UP);

            merged.merge(canal,
                    new CanalGrupoRow(canal, qtdVendas, qtdColab, vendas, comissao, bonus),
                    (a, b) -> new CanalGrupoRow(
                            canal,
                            a.quantidadeVendas() + b.quantidadeVendas(),
                            Math.max(a.quantidadeColaboradores(), b.quantidadeColaboradores()),
                            a.valorTotalVendas().add(b.valorTotalVendas()).setScale(2, RoundingMode.HALF_UP),
                            a.valorTotalComissao().add(b.valorTotalComissao()).setScale(2, RoundingMode.HALF_UP),
                            a.valorTotalBonus().add(b.valorTotalBonus()).setScale(2, RoundingMode.HALF_UP)
                    ));
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(CanalGrupoRow::valorTotalComissao).reversed())
                .toList();
    }

    public List<EquipeGrupoRow> agruparPorEquipe(
            UUID fechamentoId,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        StringBuilder jpql = new StringBuilder("""
                SELECT r.codLoja,
                       st.description,
                       r.codMarca,
                       br.description,
                       COUNT(r.id),
                       COUNT(DISTINCT r.matricula),
                       SUM(r.valorVenda),
                       SUM(r.valorComissao),
                       SUM(r.valorBonusPercentual),
                       SUM(r.valorBonusFixo)
                FROM ResultadoCalculo r
                LEFT JOIN Store st ON st.code = r.codLoja
                LEFT JOIN Brand br ON br.code = r.codMarca
                """);
        appendFiltrosResultado(jpql, "r.fechamentoId", codLoja, codMarca, idRegra, matricula);
        jpql.append(" GROUP BY r.codLoja, st.description, r.codMarca, br.description ");
        jpql.append(" ORDER BY SUM(r.valorComissao) DESC");

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        aplicarParametrosResultado(query, fechamentoId, codLoja, codMarca, idRegra, matricula);

        List<EquipeGrupoRow> lista = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            Integer loja = (Integer) row[0];
            String nomeLoja = row[1] != null ? row[1].toString() : (loja != null ? "Loja " + loja : "Loja Geral");
            Integer marca = (Integer) row[2];
            String nomeMarca = row[3] != null ? row[3].toString() : (marca != null ? "Marca " + marca : "Marca Geral");
            BigDecimal bonus = toMoney(row[8]).add(toMoney(row[9])).setScale(2, RoundingMode.HALF_UP);
            lista.add(new EquipeGrupoRow(
                    loja,
                    nomeLoja,
                    marca,
                    nomeMarca,
                    toInt(row[4]),
                    toInt(row[5]),
                    toMoney(row[6]),
                    toMoney(row[7]),
                    bonus
            ));
        }
        return lista;
    }

    public List<ColaboradorGranularRow> agruparPorColaboradorGranular(
            UUID fechamentoId,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        StringBuilder jpql = new StringBuilder("""
                SELECT r.matricula,
                       r.codCargo,
                       pos.description,
                       r.codLoja,
                       st.description,
                       r.codMarca,
                       br.description,
                       COUNT(r.id),
                       SUM(r.valorVenda),
                       SUM(r.valorComissao),
                       SUM(r.valorBonusPercentual),
                       SUM(r.valorBonusFixo)
                FROM ResultadoCalculo r
                LEFT JOIN Position pos ON pos.code = r.codCargo
                LEFT JOIN Store st ON st.code = r.codLoja
                LEFT JOIN Brand br ON br.code = r.codMarca
                """);
        appendFiltrosResultado(jpql, "r.fechamentoId", codLoja, codMarca, idRegra, matricula);
        jpql.append(" GROUP BY r.matricula, r.codCargo, pos.description, r.codLoja, st.description, r.codMarca, br.description ");
        jpql.append(" ORDER BY SUM(r.valorComissao) DESC, r.matricula ASC");

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        aplicarParametrosResultado(query, fechamentoId, codLoja, codMarca, idRegra, matricula);

        List<ColaboradorGranularRow> lista = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            String matr = row[0] != null ? row[0].toString() : "SEM_MATRICULA";
            Integer cargo = (Integer) row[1];
            String nomeCargo = row[2] != null ? row[2].toString() : (cargo != null ? "Cargo " + cargo : "Vendedor");
            Integer loja = (Integer) row[3];
            String nomeLoja = row[4] != null ? row[4].toString() : (loja != null ? "Loja " + loja : "Loja Geral");
            Integer marca = (Integer) row[5];
            String nomeMarca = row[6] != null ? row[6].toString() : (marca != null ? "Marca " + marca : "Marca Geral");
            lista.add(new ColaboradorGranularRow(
                    matr,
                    cargo,
                    nomeCargo,
                    loja,
                    nomeLoja,
                    marca,
                    nomeMarca,
                    toInt(row[7]),
                    toMoney(row[8]),
                    toMoney(row[9]),
                    toMoney(row[10]),
                    toMoney(row[11])
            ));
        }
        return lista;
    }

    public List<ColaboradorResumoRow> agruparTotaisPorColaborador(
            UUID fechamentoId,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        StringBuilder jpql = new StringBuilder("""
                SELECT r.matricula,
                       r.codCargo,
                       pos.description,
                       r.codLoja,
                       st.description,
                       COUNT(r.id),
                       SUM(r.valorVenda),
                       SUM(r.valorComissao)
                FROM ResultadoCalculo r
                LEFT JOIN Position pos ON pos.code = r.codCargo
                LEFT JOIN Store st ON st.code = r.codLoja
                """);
        appendFiltrosResultado(jpql, "r.fechamentoId", codLoja, codMarca, idRegra, matricula);
        jpql.append(" GROUP BY r.matricula, r.codCargo, pos.description, r.codLoja, st.description ");
        jpql.append(" ORDER BY SUM(r.valorComissao) DESC, r.matricula ASC");

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        aplicarParametrosResultado(query, fechamentoId, codLoja, codMarca, idRegra, matricula);

        List<ColaboradorResumoRow> lista = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            String matr = row[0] != null ? row[0].toString() : "SEM_MATRICULA";
            Integer cargo = (Integer) row[1];
            String nomeCargo = row[2] != null ? row[2].toString() : (cargo != null ? "Cargo " + cargo : "Vendedor");
            Integer loja = (Integer) row[3];
            String nomeLoja = row[4] != null ? row[4].toString() : (loja != null ? "Loja " + loja : "Loja Geral");
            lista.add(new ColaboradorResumoRow(
                    matr,
                    nomeCargo,
                    nomeLoja,
                    toLong(row[5]),
                    toMoney(row[6]),
                    toMoney(row[7])
            ));
        }
        return lista;
    }

    public List<ItemRelatorioFechamentoDTO> listarItensDetalhados(
            UUID fechamentoId,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        StringBuilder jpql = new StringBuilder("""
                SELECT r.idVenda,
                       r.protocoloCalculo,
                       r.dataVenda,
                       r.matricula,
                       r.codLoja,
                       st.description,
                       r.codMarca,
                       br.description,
                       r.codCargo,
                       pos.description,
                       s.saleChannel,
                       r.valorVenda,
                       r.taxaAplicada,
                       r.valorComissao,
                       r.valorBonusPercentual,
                       r.valorBonusFixo,
                       r.regraId,
                       r.origemTaxa,
                       r.versaoCampanhaNumero
                FROM ResultadoCalculo r
                LEFT JOIN Sale s ON s.id = r.idVenda
                LEFT JOIN Store st ON st.code = r.codLoja
                LEFT JOIN Brand br ON br.code = r.codMarca
                LEFT JOIN Position pos ON pos.code = r.codCargo
                """);
        appendFiltrosResultado(jpql, "r.fechamentoId", codLoja, codMarca, idRegra, matricula);
        jpql.append(" ORDER BY r.dataVenda ASC, r.id ASC");

        TypedQuery<Object[]> query = em.createQuery(jpql.toString(), Object[].class);
        aplicarParametrosResultado(query, fechamentoId, codLoja, codMarca, idRegra, matricula);

        List<ItemRelatorioFechamentoDTO> itens = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            Integer loja = (Integer) row[4];
            String nomeLoja = row[5] != null ? row[5].toString() : (loja != null ? "Loja " + loja : "Loja Geral");
            Integer marca = (Integer) row[6];
            String nomeMarca = row[7] != null ? row[7].toString() : (marca != null ? "Marca " + marca : "Marca Geral");
            Integer cargo = (Integer) row[8];
            String nomeCargo = row[9] != null ? row[9].toString() : (cargo != null ? "Cargo " + cargo : "Vendedor");
            String rawCanal = row[10] != null ? row[10].toString().trim() : "";
            String canal = rawCanal.isBlank() ? "PADRAO" : rawCanal;

            BigDecimal valVenda = toMoney(row[11]);
            BigDecimal taxa = row[12] != null ? (BigDecimal) row[12] : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            BigDecimal valComissaoTotal = toMoney(row[13]);
            BigDecimal valBonusPerc = toMoney(row[14]);
            BigDecimal valBonusFixo = toMoney(row[15]);
            BigDecimal valTotalBonus = valBonusPerc.add(valBonusFixo).setScale(2, RoundingMode.HALF_UP);
            BigDecimal valComissaoBase = valComissaoTotal.subtract(valTotalBonus).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            itens.add(new ItemRelatorioFechamentoDTO(
                    (UUID) row[0],
                    (UUID) row[1],
                    (LocalDate) row[2],
                    row[3] != null ? row[3].toString() : null,
                    loja,
                    nomeLoja,
                    marca,
                    nomeMarca,
                    cargo,
                    nomeCargo,
                    canal,
                    valVenda,
                    taxa,
                    valComissaoBase,
                    valBonusPerc,
                    valBonusFixo,
                    valComissaoTotal,
                    row[16] != null ? ((Number) row[16]).longValue() : null,
                    row[17] != null ? row[17].toString() : null,
                    (Integer) row[18]
            ));
        }
        return itens;
    }

    public Page<ImpedimentoFechamento> listarImpedimentosPaginados(
            UUID fechamentoId,
            String matricula,
            Integer codMarca,
            Pageable pageable
    ) {
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(i.id) FROM ImpedimentoFechamento i ");
        if (codMarca != null) {
            countJpql.append("LEFT JOIN Sale s ON s.id = i.idVenda LEFT JOIN s.brand br ");
        }
        appendFiltrosImpedimento(countJpql, null, matricula, codMarca);

        TypedQuery<Long> countQuery = em.createQuery(countJpql.toString(), Long.class);
        aplicarParametrosImpedimento(countQuery, fechamentoId, null, matricula, codMarca);
        long total = countQuery.getSingleResult();

        StringBuilder dataJpql = new StringBuilder("SELECT i FROM ImpedimentoFechamento i ");
        if (codMarca != null) {
            dataJpql.append("LEFT JOIN Sale s ON s.id = i.idVenda LEFT JOIN s.brand br ");
        }
        appendFiltrosImpedimento(dataJpql, null, matricula, codMarca);
        dataJpql.append(" ORDER BY ").append(construirOrderByImpedimento(pageable.getSort()));

        TypedQuery<ImpedimentoFechamento> dataQuery = em.createQuery(dataJpql.toString(), ImpedimentoFechamento.class);
        aplicarParametrosImpedimento(dataQuery, fechamentoId, null, matricula, codMarca);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(dataQuery.getResultList(), pageable, total);
    }

    public int contarImpedimentosFiltrados(
            UUID fechamentoId,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        if (idRegra != null) {
            return 0;
        }
        StringBuilder jpql = new StringBuilder("SELECT COUNT(i.id) FROM ImpedimentoFechamento i ");
        if (codLoja != null || codMarca != null) {
            jpql.append("LEFT JOIN Sale s ON s.id = i.idVenda ");
            if (codMarca != null) {
                jpql.append("LEFT JOIN s.brand br ");
            }
            if (codLoja != null) {
                jpql.append("LEFT JOIN s.store st ");
            }
        }
        appendFiltrosImpedimento(jpql, codLoja, matricula, codMarca);
        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
        aplicarParametrosImpedimento(query, fechamentoId, codLoja, matricula, codMarca);
        return query.getSingleResult().intValue();
    }

    private void appendFiltrosResultado(
            StringBuilder jpql,
            String fechamentoField,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        jpql.append(" WHERE ").append(fechamentoField).append(" = :fechamentoId ");
        if (codLoja != null) {
            jpql.append(" AND r.codLoja = :codLoja ");
        }
        if (codMarca != null) {
            jpql.append(" AND r.codMarca = :codMarca ");
        }
        if (idRegra != null) {
            jpql.append(" AND r.regraId = :idRegra ");
        }
        if (matricula != null && !matricula.isBlank()) {
            jpql.append(" AND LOWER(r.matricula) = LOWER(:matricula) ");
        }
    }

    private void aplicarParametrosResultado(
            TypedQuery<?> query,
            UUID fechamentoId,
            Integer codLoja,
            Integer codMarca,
            Long idRegra,
            String matricula
    ) {
        query.setParameter("fechamentoId", fechamentoId);
        if (codLoja != null) {
            query.setParameter("codLoja", codLoja);
        }
        if (codMarca != null) {
            query.setParameter("codMarca", codMarca);
        }
        if (idRegra != null) {
            query.setParameter("idRegra", idRegra);
        }
        if (matricula != null && !matricula.isBlank()) {
            query.setParameter("matricula", matricula.trim());
        }
    }

    private void appendFiltrosImpedimento(
            StringBuilder jpql,
            Integer codLoja,
            String matricula,
            Integer codMarca
    ) {
        jpql.append(" WHERE i.fechamentoId = :fechamentoId ");
        if (matricula != null && !matricula.isBlank()) {
            jpql.append(" AND LOWER(i.matricula) = LOWER(:matricula) ");
        }
        if (codMarca != null) {
            jpql.append(" AND (i.codMarca = :codMarca OR (i.codMarca IS NULL AND br.code = :codMarca)) ");
        }
        if (codLoja != null) {
            jpql.append(" AND st.code = :codLoja ");
        }
    }

    private void aplicarParametrosImpedimento(
            TypedQuery<?> query,
            UUID fechamentoId,
            Integer codLoja,
            String matricula,
            Integer codMarca
    ) {
        query.setParameter("fechamentoId", fechamentoId);
        if (matricula != null && !matricula.isBlank()) {
            query.setParameter("matricula", matricula.trim());
        }
        if (codMarca != null) {
            query.setParameter("codMarca", codMarca);
        }
        if (codLoja != null) {
            query.setParameter("codLoja", codLoja);
        }
    }

    private String construirOrderByImpedimento(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "i.dataVenda DESC, i.criadoEm DESC";
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String field = switch (order.getProperty()) {
                case "dataVenda" -> "i.dataVenda";
                case "valorVenda" -> "i.valorVenda";
                case "matricula" -> "i.matricula";
                case "tipoImpedimento" -> "i.tipoImpedimento";
                case "criadoEm" -> "i.criadoEm";
                default -> null;
            };
            if (field != null) {
                clauses.add(field + " " + (order.isAscending() ? "ASC" : "DESC"));
            }
        }
        if (clauses.isEmpty()) {
            return "i.dataVenda DESC, i.criadoEm DESC";
        }
        return String.join(", ", clauses);
    }

    private static int toInt(Object value) {
        return value != null ? ((Number) value).intValue() : 0;
    }

    private static long toLong(Object value) {
        return value != null ? ((Number) value).longValue() : 0L;
    }

    private static BigDecimal toMoney(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }
}
