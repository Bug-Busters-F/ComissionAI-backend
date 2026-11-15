package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.alerta.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.AlertaEstatisticoRepository;
import com.bugbusters.backend.repository.HistoricoVersaoRegistroRepository;
import com.bugbusters.backend.repository.LotePreparacaoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlertaEstatisticoService {
    /** Uma venda é comparada com pelo menos três outras observações do grupo. */
    private static final int AMOSTRA_MINIMA = 4;
    private static final BigDecimal LIMIAR_Z_SCORE = BigDecimal.valueOf(3);
    private static final MathContext CALCULO = new MathContext(18, RoundingMode.HALF_UP);
    private static final Set<String> CAMPOS_ORDENAVEIS = Set.of("geradoEm", "dataReferencia", "tipo", "matricula", "tamanhoAmostra", "score");

    private final AlertaEstatisticoRepository alertas;
    private final LotePreparacaoRepository lotes;
    private final SaleRepository vendas;
    private final RegistrationRepository matriculas;
    private final HistoricoVersaoRegistroRepository historicos;

    public AlertaEstatisticoService(AlertaEstatisticoRepository alertas, LotePreparacaoRepository lotes,
            SaleRepository vendas, RegistrationRepository matriculas, HistoricoVersaoRegistroRepository historicos) {
        this.alertas = alertas;
        this.lotes = lotes;
        this.vendas = vendas;
        this.matriculas = matriculas;
        this.historicos = historicos;
    }

    @Transactional
    public ReprocessamentoAlertasResponse analisarLoteEfetivado(LotePreparacao lote) {
        return analisar(lote);
    }

    @Transactional
    public ReprocessamentoAlertasResponse reprocessar(UUID loteId) {
        LotePreparacao lote = lotes.findById(loteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de preparação não encontrado: " + loteId));
        if (!"EFETIVADO".equals(lote.getStatus())) {
            throw new BusinessException("A análise só pode ser reprocessada para lote EFETIVADO.");
        }
        return analisar(lote);
    }

    @Transactional(readOnly = true)
    public Page<AlertaEstatisticoResponse> consultar(String matricula, String competencia,
            TipoAlertaEstatistico tipo, UUID loteId, boolean incluirSubstituidos, Pageable pageable) {
        validarCompetencia(competencia);
        validarOrdenacao(pageable);
        Specification<AlertaEstatistico> filtro = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (!incluirSubstituidos) ps.add(cb.isNull(root.get("substituidoEm")));
            if (matricula != null && !matricula.isBlank()) ps.add(cb.equal(root.get("matricula"), matricula.trim()));
            if (competencia != null && !competencia.isBlank()) {
                YearMonth mes = YearMonth.parse(competencia.trim());
                ps.add(cb.between(root.<LocalDate>get("dataReferencia"), mes.atDay(1), mes.atEndOfMonth()));
            }
            if (tipo != null) ps.add(cb.equal(root.get("tipo"), tipo));
            if (loteId != null) ps.add(cb.equal(root.get("lotePreparacao").get("id"), loteId));
            return cb.and(ps.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return alertas.findAll(filtro, pageable).map(this::toResponse);
    }

    private ReprocessamentoAlertasResponse analisar(LotePreparacao lote) {
        List<Sale> afetadas = vendasAfetadas(lote);
        List<Registration> registrosLote = registrosAfetados(lote);
        List<Sale> todasVendas = vendas.findAll();
        String versaoBases = versaoBases(todasVendas, matriculas.findAll());
        List<AlertaEstatistico> novos = new ArrayList<>();

        if ("HR".equalsIgnoreCase(lote.getTipoBase())) {
            Set<UUID> comVendas = afetadas.stream().map(Sale::getRegistration).filter(Objects::nonNull)
                    .map(Registration::getId).collect(Collectors.toSet());
            registrosLote.stream().filter(r -> !comVendas.contains(r.getId()))
                    .forEach(r -> novos.add(alertaSemVendasRh(lote, r, versaoBases)));
        }
        Map<String, List<Sale>> grupos = todasVendas.stream().collect(Collectors.groupingBy(this::grupo));
        for (Sale venda : afetadas) {
            List<Sale> amostra = grupos.getOrDefault(grupo(venda), List.of());
            List<Sale> referencias = amostra.stream().filter(s -> !Objects.equals(s.getId(), venda.getId())).toList();
            if (amostra.size() < AMOSTRA_MINIMA) {
                novos.add(alertaAmostraInsuficiente(lote, venda, amostra.size(), versaoBases));
                continue;
            }
            Estatistica estatistica = estatistica(referencias);
            BigDecimal z = zScore(venda.getValue(), estatistica);
            if (z != null && z.compareTo(LIMIAR_Z_SCORE) >= 0) {
                novos.add(alertaOutlier(lote, venda, amostra.size(), estatistica, z, versaoBases));
            }
        }

        List<AlertaEstatistico> atuais = alertas.findByLotePreparacaoIdAndSubstituidoEmIsNull(lote.getId());
        Set<String> assinaturasNovas = novos.stream().map(AlertaEstatistico::getAssinatura).collect(Collectors.toSet());
        Set<String> assinaturasAtuais = atuais.stream().map(AlertaEstatistico::getAssinatura).collect(Collectors.toSet());
        if (assinaturasAtuais.equals(assinaturasNovas)) {
            return new ReprocessamentoAlertasResponse(lote.getId(), versaoBases, 0, 0,
                    novos.stream().anyMatch(a -> a.getTipo() != TipoAlertaEstatistico.OUTLIER_VENDA));
        }

        List<AlertaEstatistico> salvos = novos.isEmpty() ? List.of() : alertas.saveAll(novos);
        Map<String, AlertaEstatistico> novosPorRegistro = salvos.stream()
                .collect(Collectors.toMap(this::chaveRegistro, a -> a, (a, b) -> a));
        atuais.forEach(atual -> {
            AlertaEstatistico substituto = novosPorRegistro.get(chaveRegistro(atual));
            if (substituto != null) atual.substituirPor(substituto);
            else atual.substituir();
        });
        if (!atuais.isEmpty()) alertas.saveAll(atuais);
        return new ReprocessamentoAlertasResponse(lote.getId(), versaoBases, salvos.size(), atuais.size(),
                salvos.stream().anyMatch(a -> a.getTipo() != TipoAlertaEstatistico.OUTLIER_VENDA));
    }

    private List<Sale> vendasAfetadas(LotePreparacao lote) {
        List<HistoricoVersaoRegistro> historico = historicos.findAllByLotePreparacaoId(lote.getId());
        if ("SALES".equalsIgnoreCase(lote.getTipoBase())) {
            List<UUID> ids = historico.stream().map(HistoricoVersaoRegistro::getRegistroId).distinct().toList();
            return ids.isEmpty() ? vendas.findByIdLoteUltimaAlteracao(idLote(lote)) : vendas.findAllById(ids);
        }
        List<Registration> regs = registrosAfetados(lote);
        return regs.isEmpty() ? List.of() : vendas.findByRegistrationIdIn(regs.stream().map(Registration::getId).toList());
    }

    private List<Registration> registrosAfetados(LotePreparacao lote) {
        if (!"HR".equalsIgnoreCase(lote.getTipoBase())) return List.of();
        List<UUID> ids = historicos.findAllByLotePreparacaoId(lote.getId()).stream()
                .map(HistoricoVersaoRegistro::getRegistroId).distinct().toList();
        return ids.isEmpty() ? matriculas.findByIdLoteUltimaAlteracao(idLote(lote)) : matriculas.findAllById(ids);
    }

    private String idLote(LotePreparacao lote) {
        return lote.getEnvioArquivoEfetivado() != null ? lote.getEnvioArquivoEfetivado().getIdLote() : null;
    }

    private AlertaEstatistico alertaAmostraInsuficiente(LotePreparacao lote, Sale venda, int n, String base) {
        return criar(lote, venda, TipoAlertaEstatistico.AMOSTRA_INSUFICIENTE, n, null, null, null, base);
    }

    private AlertaEstatistico alertaOutlier(LotePreparacao lote, Sale venda, int n, Estatistica e, BigDecimal z, String base) {
        return criar(lote, venda, TipoAlertaEstatistico.OUTLIER_VENDA, n, e.media(), e.desvio(), z, base);
    }

    private AlertaEstatistico alertaSemVendasRh(LotePreparacao lote, Registration registro, String base) {
        AlertaEstatistico alerta = new AlertaEstatistico();
        alerta.setLotePreparacao(lote);
        alerta.setMatricula(registro.getRegistration());
        alerta.setDataReferencia(dataReferenciaLote(lote));
        alerta.setTipo(TipoAlertaEstatistico.SEM_VENDAS_PARA_RH);
        alerta.setMetodo("Z_SCORE_LEAVE_ONE_OUT_V2");
        alerta.setGrupoComparavel("SEM_VENDAS_ASSOCIADAS");
        alerta.setTamanhoAmostra(0);
        alerta.setVersaoBases(base);
        alerta.setAssinatura(hash(lote.getId() + "|" + registro.getId() + "|SEM_VENDAS|" + base));
        return alerta;
    }

    private LocalDate dataReferenciaLote(LotePreparacao lote) {
        return lote.getCompetencia() == null || lote.getCompetencia().isBlank() ? null
                : YearMonth.parse(lote.getCompetencia()).atDay(1);
    }

    private AlertaEstatistico criar(LotePreparacao lote, Sale venda, TipoAlertaEstatistico tipo, int n,
            BigDecimal media, BigDecimal desvio, BigDecimal score, String base) {
        AlertaEstatistico alerta = new AlertaEstatistico();
        alerta.setLotePreparacao(lote);
        alerta.setVenda(venda);
        alerta.setMatricula(venda.getRegistration().getRegistration());
        alerta.setDataReferencia(venda.getSaleDate());
        alerta.setTipo(tipo);
        alerta.setMetodo("Z_SCORE_LEAVE_ONE_OUT_V2");
        alerta.setGrupoComparavel(grupo(venda));
        alerta.setTamanhoAmostra(n);
        alerta.setValorObservado(venda.getValue());
        alerta.setMediaGrupo(media);
        alerta.setDesvioPadrao(desvio);
        alerta.setScore(score);
        alerta.setVersaoBases(base);
        alerta.setAssinatura(hash(lote.getId() + "|" + venda.getId() + "|" + tipo + "|" + base));
        return alerta;
    }

    private String grupo(Sale venda) {
        return YearMonth.from(venda.getSaleDate()) + "|loja=" + venda.getStore().getId()
                + "|cargo=" + venda.getRegistration().getPosition().getId() + "|marca=" + venda.getBrand().getId();
    }

    private Estatistica estatistica(List<Sale> referencias) {
        BigDecimal quantidade = BigDecimal.valueOf(referencias.size());
        BigDecimal soma = referencias.stream().map(Sale::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal media = soma.divide(quantidade, CALCULO);
        BigDecimal somaQuadrados = referencias.stream().map(Sale::getValue)
                .map(valor -> valor.subtract(media).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variancia = referencias.size() > 1
                ? somaQuadrados.divide(BigDecimal.valueOf(referencias.size() - 1L), CALCULO) : BigDecimal.ZERO;
        return new Estatistica(media.setScale(2, RoundingMode.HALF_UP), variancia.sqrt(CALCULO).setScale(4, RoundingMode.HALF_UP));
    }

    private BigDecimal zScore(BigDecimal valor, Estatistica estatistica) {
        if (estatistica.desvio().compareTo(BigDecimal.ZERO) == 0) {
            return valor.compareTo(estatistica.media()) == 0 ? null : LIMIAR_Z_SCORE;
        }
        return valor.subtract(estatistica.media()).abs().divide(estatistica.desvio(), 6, RoundingMode.HALF_UP);
    }

    private String versaoBases(List<Sale> todasVendas, List<Registration> todasMatriculas) {
        String vendasVersao = todasVendas.stream().sorted(Comparator.comparing(Sale::getId))
                .map(s -> "V:" + s.getId() + ":" + s.getVersao() + ":" + s.getRegistration().getId() + ":" + s.getRegistration().getVersao())
                .collect(Collectors.joining("|"));
        String matriculasVersao = todasMatriculas.stream().sorted(Comparator.comparing(Registration::getId))
                .map(r -> "R:" + r.getId() + ":" + r.getVersao()).collect(Collectors.joining("|"));
        return hash(vendasVersao + "|" + matriculasVersao);
    }

    private String chaveRegistro(AlertaEstatistico alerta) {
        return alerta.getVenda() != null ? "V:" + alerta.getVenda().getId() : "R:" + alerta.getMatricula();
    }

    private void validarCompetencia(String competencia) {
        if (competencia == null || competencia.isBlank()) return;
        try { YearMonth.parse(competencia.trim()); }
        catch (java.time.format.DateTimeParseException ex) { throw new BusinessException("Competência inválida. Utilize o formato AAAA-MM."); }
    }

    private void validarOrdenacao(Pageable pageable) {
        for (Sort.Order ordem : pageable.getSort()) {
            if (!CAMPOS_ORDENAVEIS.contains(ordem.getProperty())) {
                throw new BusinessException("Campo de ordenação inválido: " + ordem.getProperty());
            }
        }
    }

    private String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private AlertaEstatisticoResponse toResponse(AlertaEstatistico alerta) {
        return new AlertaEstatisticoResponse(alerta.getId(), alerta.getLotePreparacao().getId(),
                alerta.getVenda() != null ? alerta.getVenda().getId() : null, alerta.getMatricula(), alerta.getDataReferencia(),
                alerta.getTipo().name(), alerta.getMetodo(), alerta.getGrupoComparavel(), alerta.getTamanhoAmostra(),
                alerta.getValorObservado(), alerta.getMediaGrupo(), alerta.getDesvioPadrao(), alerta.getScore(),
                alerta.getVersaoBases(), alerta.getGeradoEm(), alerta.getSubstituidoEm(),
                alerta.getSubstituidoPor() != null ? alerta.getSubstituidoPor().getId() : null);
    }

    private record Estatistica(BigDecimal media, BigDecimal desvio) { }
}
