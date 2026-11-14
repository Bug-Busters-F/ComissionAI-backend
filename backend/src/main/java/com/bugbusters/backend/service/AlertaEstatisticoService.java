package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.alerta.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.AlertaEstatisticoRepository;
import com.bugbusters.backend.repository.LotePreparacaoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlertaEstatisticoService {
    private static final int AMOSTRA_MINIMA = 3;
    private static final BigDecimal LIMIAR_Z_SCORE = BigDecimal.valueOf(3);
    private final AlertaEstatisticoRepository alertas;
    private final LotePreparacaoRepository lotes;
    private final SaleRepository vendas;
    private final RegistrationRepository matriculas;

    public AlertaEstatisticoService(AlertaEstatisticoRepository alertas, LotePreparacaoRepository lotes,
            SaleRepository vendas, RegistrationRepository matriculas) {
        this.alertas = alertas; this.lotes = lotes; this.vendas = vendas; this.matriculas = matriculas;
    }

    /** Chamado ao fim de cada efetivação. Mantém a análise na mesma transação da alteração de base. */
    @Transactional
    public ReprocessamentoAlertasResponse analisarLoteEfetivado(LotePreparacao lote) {
        return analisar(lote, false);
    }

    @Transactional
    public ReprocessamentoAlertasResponse reprocessar(UUID loteId) {
        LotePreparacao lote = lotes.findById(loteId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de preparação não encontrado: " + loteId));
        if (!"EFETIVADO".equals(lote.getStatus())) throw new BusinessException("A análise só pode ser reprocessada para lote EFETIVADO.");
        return analisar(lote, true);
    }

    @Transactional(readOnly = true)
    public Page<AlertaEstatisticoResponse> consultar(String matricula, String competencia,
            TipoAlertaEstatistico tipo, UUID loteId, boolean incluirSubstituidos, Pageable pageable) {
        Specification<AlertaEstatistico> filtro = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (!incluirSubstituidos) ps.add(cb.isNull(root.get("substituidoEm")));
            if (matricula != null && !matricula.isBlank()) ps.add(cb.equal(root.get("matricula"), matricula.trim()));
            if (competencia != null && !competencia.isBlank()) {
                YearMonth mes = YearMonth.parse(competencia);
                ps.add(cb.between(root.<LocalDate>get("dataReferencia"), mes.atDay(1), mes.atEndOfMonth()));
            }
            if (tipo != null) ps.add(cb.equal(root.get("tipo"), tipo));
            if (loteId != null) ps.add(cb.equal(root.get("lotePreparacao").get("id"), loteId));
            return cb.and(ps.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return alertas.findAll(filtro, pageable).map(this::toResponse);
    }

    private ReprocessamentoAlertasResponse analisar(LotePreparacao lote, boolean reprocessamentoManual) {
        List<Sale> afetadas = vendasAfetadas(lote);
        List<Sale> todasVendas = vendas.findAll();
        String versaoBases = versaoBases(todasVendas);
        List<AlertaEstatistico> novos = new ArrayList<>();

        if (afetadas.isEmpty() && "HR".equalsIgnoreCase(lote.getTipoBase())) {
            for (Registration r : matriculas.findByIdLoteUltimaAlteracao(lote.getEnvioArquivoEfetivado().getIdLote())) {
                novos.add(alertaSemVendasRh(lote, r, versaoBases));
            }
        }
        Map<String, List<Sale>> grupos = todasVendas.stream().collect(Collectors.groupingBy(this::grupo));
        for (Sale venda : afetadas) {
            List<Sale> amostra = grupos.getOrDefault(grupo(venda), List.of());
            if (amostra.size() < AMOSTRA_MINIMA) novos.add(alertaAmostraInsuficiente(lote, venda, amostra.size(), versaoBases));
            else {
                Estatistica e = estatistica(amostra);
                if (e.desvio().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal z = venda.getValue().subtract(e.media()).divide(e.desvio(), 6, RoundingMode.HALF_UP).abs();
                    if (z.compareTo(LIMIAR_Z_SCORE) >= 0) novos.add(alertaOutlier(lote, venda, amostra.size(), e, z, versaoBases));
                }
            }
        }
        List<AlertaEstatistico> atuais = alertas.findByLotePreparacaoIdAndSubstituidoEmIsNull(lote.getId());
        Set<String> assinaturasNovas = novos.stream().map(AlertaEstatistico::getAssinatura).collect(Collectors.toSet());
        Set<String> assinaturasAtuais = atuais.stream().map(AlertaEstatistico::getAssinatura).collect(Collectors.toSet());
        if (assinaturasAtuais.equals(assinaturasNovas))
            return new ReprocessamentoAlertasResponse(lote.getId(), versaoBases, 0, 0, novos.stream().anyMatch(a -> a.getTipo() != TipoAlertaEstatistico.OUTLIER_VENDA));
        atuais.forEach(AlertaEstatistico::substituir);
        if (!atuais.isEmpty()) alertas.saveAll(atuais);
        if (!novos.isEmpty()) alertas.saveAll(novos);
        return new ReprocessamentoAlertasResponse(lote.getId(), versaoBases, novos.size(), atuais.size(),
                novos.stream().anyMatch(a -> a.getTipo() != TipoAlertaEstatistico.OUTLIER_VENDA));
    }

    private List<Sale> vendasAfetadas(LotePreparacao lote) {
        String idLote = lote.getEnvioArquivoEfetivado().getIdLote();
        if ("SALES".equalsIgnoreCase(lote.getTipoBase())) return vendas.findByIdLoteUltimaAlteracao(idLote);
        List<Registration> regs = matriculas.findByIdLoteUltimaAlteracao(idLote);
        return regs.isEmpty() ? List.of() : vendas.findByRegistrationIdIn(regs.stream().map(Registration::getId).toList());
    }

    private AlertaEstatistico alertaAmostraInsuficiente(LotePreparacao lote, Sale venda, int n, String base) {
        return criar(lote, venda, TipoAlertaEstatistico.AMOSTRA_INSUFICIENTE, n, null, null, null, base);
    }
    private AlertaEstatistico alertaOutlier(LotePreparacao lote, Sale venda, int n, Estatistica e, BigDecimal z, String base) {
        return criar(lote, venda, TipoAlertaEstatistico.OUTLIER_VENDA, n, e.media(), e.desvio(), z, base);
    }
    private AlertaEstatistico alertaSemVendasRh(LotePreparacao lote, Registration r, String base) {
        AlertaEstatistico a = new AlertaEstatistico(); a.setLotePreparacao(lote); a.setMatricula(r.getRegistration());
        a.setTipo(TipoAlertaEstatistico.SEM_VENDAS_PARA_RH); a.setMetodo("Z_SCORE_GRUPO_VENDAS_V1"); a.setGrupoComparavel("SEM_VENDAS_ASSOCIADAS");
        a.setTamanhoAmostra(0); a.setVersaoBases(base); a.setAssinatura(hash(lote.getId() + "|" + r.getId() + "|SEM_VENDAS|" + base)); return a;
    }
    private AlertaEstatistico criar(LotePreparacao lote, Sale venda, TipoAlertaEstatistico tipo, int n,
            BigDecimal media, BigDecimal desvio, BigDecimal score, String base) {
        AlertaEstatistico a = new AlertaEstatistico(); a.setLotePreparacao(lote); a.setVenda(venda); a.setMatricula(venda.getRegistration().getRegistration());
        a.setDataReferencia(venda.getSaleDate()); a.setTipo(tipo); a.setMetodo("Z_SCORE_GRUPO_VENDAS_V1"); a.setGrupoComparavel(grupo(venda)); a.setTamanhoAmostra(n);
        a.setValorObservado(venda.getValue()); a.setMediaGrupo(media); a.setDesvioPadrao(desvio); a.setScore(score); a.setVersaoBases(base);
        a.setAssinatura(hash(lote.getId() + "|" + venda.getId() + "|" + tipo + "|" + base)); return a;
    }
    private String grupo(Sale v) { return YearMonth.from(v.getSaleDate()) + "|loja=" + v.getStore().getId() + "|cargo=" + v.getRegistration().getPosition().getId() + "|marca=" + v.getBrand().getId(); }
    private Estatistica estatistica(List<Sale> amostra) {
        double media = amostra.stream().mapToDouble(s -> s.getValue().doubleValue()).average().orElse(0);
        double variancia = amostra.stream().mapToDouble(s -> Math.pow(s.getValue().doubleValue() - media, 2)).average().orElse(0);
        return new Estatistica(BigDecimal.valueOf(media).setScale(2, RoundingMode.HALF_UP), BigDecimal.valueOf(Math.sqrt(variancia)).setScale(4, RoundingMode.HALF_UP));
    }
    private String versaoBases(List<Sale> todas) { return hash(todas.stream().sorted(Comparator.comparing(Sale::getId)).map(s -> s.getId()+":"+s.getVersao()+":"+s.getRegistration().getId()+":"+s.getRegistration().getVersao()).collect(Collectors.joining("|"))); }
    private String hash(String valor) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private AlertaEstatisticoResponse toResponse(AlertaEstatistico a) { return new AlertaEstatisticoResponse(a.getId(), a.getLotePreparacao().getId(), a.getVenda() != null ? a.getVenda().getId() : null, a.getMatricula(), a.getDataReferencia(), a.getTipo().name(), a.getMetodo(), a.getGrupoComparavel(), a.getTamanhoAmostra(), a.getValorObservado(), a.getMediaGrupo(), a.getDesvioPadrao(), a.getScore(), a.getVersaoBases(), a.getGeradoEm()); }
    private record Estatistica(BigDecimal media, BigDecimal desvio) { }
}
