package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.alerta.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.AlertaEstatisticoRepository;
import com.bugbusters.backend.repository.CampanhaAnexoBaseRepository;
import com.bugbusters.backend.repository.HistoricoVersaoRegistroRepository;
import com.bugbusters.backend.repository.LinhaPreparacaoRepository;
import com.bugbusters.backend.repository.LotePreparacaoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlertaEstatisticoService {
    private static final Logger log = LoggerFactory.getLogger(AlertaEstatisticoService.class);
    /** Uma venda é comparada com pelo menos três outras observações do grupo. */
    private static final int AMOSTRA_MINIMA = 4;
    private static final int TAMANHO_LOTE_IN = 1000;
    private static final BigDecimal LIMIAR_Z_SCORE = BigDecimal.valueOf(3);
    private static final MathContext CALCULO = new MathContext(18, RoundingMode.HALF_UP);
    private static final Set<String> CAMPOS_ORDENAVEIS = Set.of("geradoEm", "dataReferencia", "tipo", "matricula", "tamanhoAmostra", "score");

    private final AlertaEstatisticoRepository alertas;
    private final LotePreparacaoRepository lotes;
    private final SaleRepository vendas;
    private final RegistrationRepository matriculas;
    private final HistoricoVersaoRegistroRepository historicos;
    private final LinhaPreparacaoRepository linhasPreparacao;
    private final CampanhaAnexoBaseRepository anexosCampanha;
    private final TaskExecutor alertaExecutor;
    private final TransactionTemplate requiresNewTx;
    private final Object monitorAnalises = new Object();
    private int analisesEmAndamento = 0;

    public AlertaEstatisticoService(AlertaEstatisticoRepository alertas, LotePreparacaoRepository lotes,
            SaleRepository vendas, RegistrationRepository matriculas, HistoricoVersaoRegistroRepository historicos,
            LinhaPreparacaoRepository linhasPreparacao,
            CampanhaAnexoBaseRepository anexosCampanha,
            @Qualifier("alertaEstatisticoExecutor") TaskExecutor alertaExecutor,
            PlatformTransactionManager transactionManager) {
        this.alertas = alertas;
        this.lotes = lotes;
        this.vendas = vendas;
        this.matriculas = matriculas;
        this.historicos = historicos;
        this.linhasPreparacao = linhasPreparacao;
        this.anexosCampanha = anexosCampanha;
        this.alertaExecutor = alertaExecutor;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public ReprocessamentoAlertasResponse analisarLoteEfetivado(LotePreparacao lote) {
        return analisar(lote);
    }

    /**
     * Agenda a análise estatística para execução assíncrona após o commit da efetivação,
     * evitando que o cálculo de alertas bloqueie a transação e o retorno HTTP ao usuário.
     */
    public void agendarAnalisePosCommit(UUID loteId) {
        if (loteId == null) return;
        Runnable tarefa = () -> executarAnaliseAssincrona(loteId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    despacharAssincrono(tarefa);
                }
            });
        } else {
            despacharAssincrono(tarefa);
        }
    }

    private void despacharAssincrono(Runnable tarefa) {
        synchronized (monitorAnalises) {
            analisesEmAndamento++;
        }
        Runnable envolvida = () -> {
            try {
                tarefa.run();
            } finally {
                synchronized (monitorAnalises) {
                    analisesEmAndamento = Math.max(0, analisesEmAndamento - 1);
                    if (analisesEmAndamento == 0) {
                        monitorAnalises.notifyAll();
                    }
                }
            }
        };
        if (alertaExecutor != null) {
            try {
                alertaExecutor.execute(envolvida);
            } catch (RuntimeException ex) {
                synchronized (monitorAnalises) {
                    analisesEmAndamento = Math.max(0, analisesEmAndamento - 1);
                    if (analisesEmAndamento == 0) {
                        monitorAnalises.notifyAll();
                    }
                }
                throw ex;
            }
        } else {
            envolvida.run();
        }
    }

    /**
     * Aguarda a conclusão de análises estatísticas assíncronas em andamento (útil em testes e desligamento gracioso).
     */
    public void aguardarAnalisesPendentes(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        synchronized (monitorAnalises) {
            while (analisesEmAndamento > 0) {
                long restante = deadline - System.currentTimeMillis();
                if (restante <= 0) break;
                try {
                    monitorAnalises.wait(restante);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void executarAnaliseAssincrona(UUID loteId) {
        try {
            requiresNewTx.executeWithoutResult(status -> lotes.findByIdForUpdate(loteId)
                    .filter(l -> "EFETIVADO".equals(l.getStatus()))
                    .ifPresent(this::analisar));
        } catch (Exception ex) {
            log.error("Falha na análise estatística assíncrona do lote {}: {}", loteId, ex.getMessage(), ex);
        }
    }

    @Transactional
    public ReprocessamentoAlertasResponse reprocessar(UUID loteId) {
        LotePreparacao lote = lotes.findByIdForUpdate(loteId)
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

    private ReprocessamentoAlertasResponse analisar(LotePreparacao loteParam) {
        // Adquire trava pessimista no lote para serializar análises concorrentes (pós-commit + reprocessar)
        LotePreparacao lote = loteParam.getId() != null
                ? lotes.findByIdForUpdate(loteParam.getId()).orElse(loteParam)
                : loteParam;
        List<YearMonth> competenciasAlvo = resolverCompetenciasAnalise(lote);
        List<Registration> registrosLote = registrosAfetados(lote);
        List<Sale> afetadas = vendasAfetadas(lote, registrosLote, competenciasAlvo);

        TreeSet<YearMonth> competenciasNecessarias = new TreeSet<>(competenciasAlvo);
        for (Sale venda : afetadas) {
            if (venda.getSaleDate() != null) {
                competenciasNecessarias.add(YearMonth.from(venda.getSaleDate()));
            }
        }

        List<Sale> vendasReferencia;
        if (competenciasNecessarias.isEmpty()) {
            vendasReferencia = List.of();
        } else {
            LocalDate inicioRef = competenciasNecessarias.first().atDay(1);
            LocalDate fimRef = competenciasNecessarias.last().atEndOfMonth();
            vendasReferencia = vendas.findVendasPorPeriodoComRelacionamentos(inicioRef, fimRef).stream()
                    .filter(s -> s.getSaleDate() != null && competenciasNecessarias.contains(YearMonth.from(s.getSaleDate())))
                    .toList();
        }

        Map<UUID, Registration> matriculasReferenciaMap = new LinkedHashMap<>();
        for (Registration r : registrosLote) {
            if (r != null && r.getId() != null) matriculasReferenciaMap.put(r.getId(), r);
        }
        for (Sale s : vendasReferencia) {
            if (s.getRegistration() != null && s.getRegistration().getId() != null) {
                matriculasReferenciaMap.putIfAbsent(s.getRegistration().getId(), s.getRegistration());
            }
        }

        String versaoBases = versaoBases(vendasReferencia, new ArrayList<>(matriculasReferenciaMap.values()));
        List<AlertaEstatistico> novos = new ArrayList<>();

        if ("HR".equalsIgnoreCase(lote.getTipoBase())) {
            if (competenciasAlvo.isEmpty()) {
                Set<UUID> comVendas = afetadas.stream().map(Sale::getRegistration).filter(Objects::nonNull)
                        .map(Registration::getId).collect(Collectors.toSet());
                registrosLote.stream().filter(r -> !comVendas.contains(r.getId()))
                        .forEach(r -> novos.add(alertaSemVendasRh(lote, r, null, versaoBases)));
            } else {
                for (YearMonth comp : competenciasAlvo) {
                    Set<UUID> comVendasNaComp = afetadas.stream()
                            .filter(s -> s.getSaleDate() != null && comp.equals(YearMonth.from(s.getSaleDate())))
                            .map(Sale::getRegistration).filter(Objects::nonNull)
                            .map(Registration::getId).collect(Collectors.toSet());
                    registrosLote.stream().filter(r -> !comVendasNaComp.contains(r.getId()))
                            .forEach(r -> novos.add(alertaSemVendasRh(lote, r, comp, versaoBases)));
                }
            }
        }
        Map<String, List<Sale>> grupos = vendasReferencia.stream().collect(Collectors.groupingBy(this::grupo));
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

        // Deduplica novos alertas por assinatura em memória para garantir unicidade antes da persistência
        Map<String, AlertaEstatistico> novosUnicosPorAssinatura = new LinkedHashMap<>();
        for (AlertaEstatistico novo : novos) {
            novosUnicosPorAssinatura.putIfAbsent(novo.getAssinatura(), novo);
        }
        List<AlertaEstatistico> novosUnicos = new ArrayList<>(novosUnicosPorAssinatura.values());

        List<AlertaEstatistico> atuais = alertas.findByLotePreparacaoIdAndSubstituidoEmIsNull(lote.getId());
        Set<String> assinaturasNovas = novosUnicosPorAssinatura.keySet();
        Set<String> assinaturasAtuais = atuais.stream().map(AlertaEstatistico::getAssinatura).collect(Collectors.toSet());
        boolean semDuplicatasAtivas = atuais.size() == assinaturasAtuais.size();
        if (semDuplicatasAtivas && atuais.size() == novosUnicos.size() && assinaturasAtuais.equals(assinaturasNovas)) {
            return new ReprocessamentoAlertasResponse(lote.getId(), versaoBases, 0, 0,
                    novosUnicos.stream().anyMatch(a -> a.getTipo() != TipoAlertaEstatistico.OUTLIER_VENDA));
        }

        // Marca os alertas ativos anteriores como substituídos e dá flush ANTES de inserir os novos
        // para respeitar o índice único parcial (lote_preparacao_id, assinatura) WHERE substituido_em IS NULL
        if (!atuais.isEmpty()) {
            atuais.forEach(AlertaEstatistico::substituir);
            alertas.saveAllAndFlush(atuais);
        }

        List<AlertaEstatistico> salvos = novosUnicos.isEmpty() ? List.of() : alertas.saveAllAndFlush(novosUnicos);
        if (!atuais.isEmpty() && !salvos.isEmpty()) {
            Map<String, AlertaEstatistico> novosPorRegistro = salvos.stream()
                    .collect(Collectors.toMap(this::chaveRegistro, a -> a, (a, b) -> a));
            atuais.forEach(atual -> {
                AlertaEstatistico substituto = novosPorRegistro.get(chaveRegistro(atual));
                if (substituto != null) atual.substituirPor(substituto);
            });
            alertas.saveAll(atuais);
        }
        return new ReprocessamentoAlertasResponse(lote.getId(), versaoBases, salvos.size(), atuais.size(),
                salvos.stream().anyMatch(a -> a.getTipo() != TipoAlertaEstatistico.OUTLIER_VENDA));
    }

    private List<YearMonth> resolverCompetenciasAnalise(LotePreparacao lote) {
        if (lote.getCompetencia() != null && !lote.getCompetencia().isBlank()) {
            return List.of(YearMonth.parse(lote.getCompetencia().trim()));
        }
        if ("HR".equalsIgnoreCase(lote.getTipoBase()) && lote.getCampanha() != null) {
            Campanha campanha = lote.getCampanha();
            TreeSet<YearMonth> competenciasAnexadas = new TreeSet<>();
            if (anexosCampanha != null && campanha.getId() != null) {
                List<CampanhaAnexoBase> anexosVendas = anexosCampanha
                        .findByCampanhaIdAndTipoBaseAndDesanexadoEmIsNull(campanha.getId(), "SALES");
                for (CampanhaAnexoBase anexo : anexosVendas) {
                    if (anexo.getCompetencia() != null && anexo.getCompetencia().matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                        competenciasAnexadas.add(YearMonth.parse(anexo.getCompetencia()));
                    }
                }
            }
            if (!competenciasAnexadas.isEmpty()) {
                return new ArrayList<>(competenciasAnexadas);
            }
            LocalDate dataInicio;
            if (campanha.getVersaoVigente() != null && campanha.getVersaoVigente().getDataInicio() != null) {
                dataInicio = campanha.getVersaoVigente().getDataInicio();
            } else if (campanha.getVersaoProposta() != null && campanha.getVersaoProposta().getDataInicio() != null) {
                dataInicio = campanha.getVersaoProposta().getDataInicio();
            } else {
                dataInicio = campanha.getDataInicio();
            }
            if (dataInicio != null) {
                return List.of(YearMonth.from(dataInicio));
            }
        }
        return List.of();
    }

    private List<Sale> vendasAfetadas(LotePreparacao lote, List<Registration> regs, List<YearMonth> competenciasAlvo) {
        if ("SALES".equalsIgnoreCase(lote.getTipoBase())) {
            List<HistoricoVersaoRegistro> historico = historicos.findAllByLotePreparacaoId(lote.getId());
            List<UUID> ids = historico.stream().map(HistoricoVersaoRegistro::getRegistroId).distinct().toList();
            if (!ids.isEmpty()) {
                return buscarVendasPorIdsEmLotes(ids);
            }
            List<Sale> porLote = vendas.findByIdLoteUltimaAlteracao(idLote(lote));
            if (!porLote.isEmpty() || competenciasAlvo.isEmpty()) {
                return porLote;
            }
            LocalDate inicio = competenciasAlvo.get(0).atDay(1);
            LocalDate fim = competenciasAlvo.get(competenciasAlvo.size() - 1).atEndOfMonth();
            return vendas.findVendasPorPeriodoComRelacionamentos(inicio, fim);
        }
        if (regs.isEmpty()) return List.of();
        List<UUID> regIds = regs.stream().map(Registration::getId).filter(Objects::nonNull).distinct().toList();
        if (regIds.isEmpty()) return List.of();
        if (competenciasAlvo.isEmpty()) {
            return buscarVendasPorRegistrationsEmLotes(regIds, null, null);
        }
        LocalDate inicio = competenciasAlvo.get(0).atDay(1);
        LocalDate fim = competenciasAlvo.get(competenciasAlvo.size() - 1).atEndOfMonth();
        Set<YearMonth> alvoSet = new HashSet<>(competenciasAlvo);
        return buscarVendasPorRegistrationsEmLotes(regIds, inicio, fim).stream()
                .filter(s -> s.getSaleDate() != null && alvoSet.contains(YearMonth.from(s.getSaleDate())))
                .toList();
    }

    private List<Sale> buscarVendasPorIdsEmLotes(List<UUID> ids) {
        List<Sale> resultado = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i += TAMANHO_LOTE_IN) {
            List<UUID> sub = ids.subList(i, Math.min(i + TAMANHO_LOTE_IN, ids.size()));
            resultado.addAll(vendas.findByIdIn(sub));
        }
        return resultado;
    }

    private List<Sale> buscarVendasPorRegistrationsEmLotes(List<UUID> regIds, LocalDate inicio, LocalDate fim) {
        List<Sale> resultado = new ArrayList<>();
        for (int i = 0; i < regIds.size(); i += TAMANHO_LOTE_IN) {
            List<UUID> sub = regIds.subList(i, Math.min(i + TAMANHO_LOTE_IN, regIds.size()));
            if (inicio != null && fim != null) {
                resultado.addAll(vendas.findByRegistrationIdInAndSaleDateBetween(sub, inicio, fim));
            } else {
                resultado.addAll(vendas.findByRegistrationIdIn(sub));
            }
        }
        return resultado;
    }

    private List<Registration> registrosAfetados(LotePreparacao lote) {
        if (!"HR".equalsIgnoreCase(lote.getTipoBase())) return List.of();
        Map<UUID, Registration> porId = new LinkedHashMap<>();

        List<UUID> idsHistorico = historicos.findAllByLotePreparacaoId(lote.getId()).stream()
                .map(HistoricoVersaoRegistro::getRegistroId).filter(Objects::nonNull).distinct().toList();
        if (!idsHistorico.isEmpty()) {
            matriculas.findAllById(idsHistorico).forEach(r -> porId.put(r.getId(), r));
        }

        if (linhasPreparacao != null) {
            List<LinhaPreparacao> linhasAtivas = linhasPreparacao.findByLotePreparacaoIdOrderByNumeroLinhaAsc(lote.getId())
                    .stream().filter(l -> !l.isIgnorarNaEfetivacao()).toList();
            List<UUID> idsAlvo = linhasAtivas.stream().map(LinhaPreparacao::getRegistroAlvoId)
                    .filter(Objects::nonNull).filter(id -> !porId.containsKey(id)).distinct().toList();
            for (int i = 0; i < idsAlvo.size(); i += TAMANHO_LOTE_IN) {
                matriculas.findAllById(idsAlvo.subList(i, Math.min(i + TAMANHO_LOTE_IN, idsAlvo.size())))
                        .forEach(r -> porId.putIfAbsent(r.getId(), r));
            }
            Set<String> matsFaltantes = linhasAtivas.stream()
                    .filter(l -> l.getRegistroAlvoId() == null && l.getMatricula() != null && !l.getMatricula().isBlank())
                    .map(l -> l.getMatricula().trim())
                    .collect(Collectors.toSet());
            if (!matsFaltantes.isEmpty()) {
                List<String> matsList = new ArrayList<>(matsFaltantes);
                for (int i = 0; i < matsList.size(); i += TAMANHO_LOTE_IN) {
                    matriculas.findByRegistrationIn(matsList.subList(i, Math.min(i + TAMANHO_LOTE_IN, matsList.size())))
                            .forEach(r -> porId.putIfAbsent(r.getId(), r));
                }
            }
        }

        if (porId.isEmpty()) {
            String loteIdStr = idLote(lote);
            if (loteIdStr != null) {
                matriculas.findByIdLoteUltimaAlteracao(loteIdStr).forEach(r -> porId.putIfAbsent(r.getId(), r));
            }
        }
        return new ArrayList<>(porId.values());
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

    private AlertaEstatistico alertaSemVendasRh(LotePreparacao lote, Registration registro, YearMonth competencia, String base) {
        AlertaEstatistico alerta = new AlertaEstatistico();
        alerta.setLotePreparacao(lote);
        alerta.setMatricula(registro.getRegistration());
        alerta.setDataReferencia(competencia != null ? competencia.atDay(1) : dataReferenciaLote(lote));
        alerta.setTipo(TipoAlertaEstatistico.SEM_VENDAS_PARA_RH);
        alerta.setMetodo("Z_SCORE_LEAVE_ONE_OUT_V2");
        alerta.setGrupoComparavel("SEM_VENDAS_ASSOCIADAS");
        alerta.setTamanhoAmostra(0);
        alerta.setVersaoBases(base);
        String compChave = competencia != null ? competencia.toString() : "GLOBAL";
        alerta.setAssinatura(hash(lote.getId() + "|" + registro.getId() + "|" + compChave + "|SEM_VENDAS|" + base));
        return alerta;
    }

    private LocalDate dataReferenciaLote(LotePreparacao lote) {
        return lote.getCompetencia() == null || lote.getCompetencia().isBlank() ? null
                : YearMonth.parse(lote.getCompetencia()).atDay(1);
    }

    private AlertaEstatistico criar(LotePreparacao lote, Sale venda, TipoAlertaEstatistico tipo, int n,
            BigDecimal media, BigDecimal desvio, BigDecimal score, String base) {
        AlertaEstatistico alerta = new AlertaEstatistico();
        String grupoComparavel = grupo(venda);
        alerta.setLotePreparacao(lote);
        alerta.setVenda(venda);
        alerta.setMatricula(venda.getRegistration().getRegistration());
        alerta.setDataReferencia(venda.getSaleDate());
        alerta.setTipo(tipo);
        alerta.setMetodo("Z_SCORE_LEAVE_ONE_OUT_V2");
        alerta.setGrupoComparavel(grupoComparavel);
        alerta.setTamanhoAmostra(n);
        alerta.setValorObservado(venda.getValue());
        alerta.setMediaGrupo(media);
        alerta.setDesvioPadrao(desvio);
        alerta.setScore(score);
        alerta.setVersaoBases(base);
        alerta.setAssinatura(hash(lote.getId() + "|" + venda.getId() + "|" + tipo + "|" + grupoComparavel + "|" + base));
        return alerta;
    }

    private String grupo(Sale venda) {
        return YearMonth.from(venda.getSaleDate()) + "|loja=" + venda.getStore().getCode()
                + "|cargo=" + venda.getRegistration().getPosition().getCode() + "|marca=" + venda.getBrand().getCode();
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
        if (alerta.getVenda() != null) return "V:" + alerta.getVenda().getId();
        return "R:" + alerta.getMatricula() + ":" + (alerta.getDataReferencia() != null ? alerta.getDataReferencia() : "SEM_DATA");
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
