package com.bugbusters.backend.service.preparacao;

import com.bugbusters.backend.dto.preparacao.impacto.*;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável pela identificação, cálculo da prévia e propagação de impactos
 * de correções realizadas em lotes de preparação sobre campanhas, regras, simulações,
 * cálculos emitidos e aprovações de código (BACK-6).
 */
@Service
public class PropagacaoImpactoCorrecaoService {

    private static final Logger log = LoggerFactory.getLogger(PropagacaoImpactoCorrecaoService.class);
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;
    private final RegraRepository regraRepository;
    private final SimulacaoRepository simulacaoRepository;
    private final SimulacaoVendaRepository simulacaoVendaRepository;
    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final SnapshotResultadoCalculoRepository snapshotResultadoCalculoRepository;
    private final ExecucaoCalculoRepository execucaoCalculoRepository;
    private final FechamentoCompetenciaRepository fechamentoCompetenciaRepository;
    private final PropagacaoImpactoBaseRepository propagacaoImpactoBaseRepository;
    private final RegistrationRepository registrationRepository;
    private final SaleRepository saleRepository;

    public PropagacaoImpactoCorrecaoService(
            CampanhaRepository campanhaRepository,
            CampanhaVersaoRepository campanhaVersaoRepository,
            RegraRepository regraRepository,
            SimulacaoRepository simulacaoRepository,
            SimulacaoVendaRepository simulacaoVendaRepository,
            ResultadoCalculoRepository resultadoCalculoRepository,
            SnapshotResultadoCalculoRepository snapshotResultadoCalculoRepository,
            ExecucaoCalculoRepository execucaoCalculoRepository,
            FechamentoCompetenciaRepository fechamentoCompetenciaRepository,
            PropagacaoImpactoBaseRepository propagacaoImpactoBaseRepository,
            RegistrationRepository registrationRepository,
            SaleRepository saleRepository) {
        this.campanhaRepository = campanhaRepository;
        this.campanhaVersaoRepository = campanhaVersaoRepository;
        this.regraRepository = regraRepository;
        this.simulacaoRepository = simulacaoRepository;
        this.simulacaoVendaRepository = simulacaoVendaRepository;
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.snapshotResultadoCalculoRepository = snapshotResultadoCalculoRepository;
        this.execucaoCalculoRepository = execucaoCalculoRepository;
        this.fechamentoCompetenciaRepository = fechamentoCompetenciaRepository;
        this.propagacaoImpactoBaseRepository = propagacaoImpactoBaseRepository;
        this.registrationRepository = registrationRepository;
        this.saleRepository = saleRepository;
    }

    /**
     * Calcula o raio-X completo e determinístico de impactos de um lote de preparação.
     */
    @Transactional(readOnly = true)
    public PreviaImpactoCorrecaoDTO calcularPreviaImpacto(LotePreparacao lote, List<LinhaPreparacao> linhasAtivas) {
        if (lote == null) {
            throw new IllegalArgumentException("Lote de preparação não pode ser nulo.");
        }

        List<String> bloqueiosFechamento = new ArrayList<>();
        Set<String> competenciasAtingidas = new TreeSet<>();
        Set<String> matriculasAtingidas = new TreeSet<>();
        Set<Integer> lojasAtingidas = new TreeSet<>();
        Set<Integer> cargosAtingidos = new TreeSet<>();
        Set<Integer> marcasAtingidas = new TreeSet<>();

        Set<UUID> idsVendasAlteradas = new HashSet<>();
        Set<String> matriculasAlteradasRh = new HashSet<>();

        int registrosNovos = 0;
        int registrosAtualizados = 0;
        int registrosInalterados = 0;

        if ("HR".equalsIgnoreCase(lote.getTipoBase())) {
            Set<String> mats = linhasAtivas.stream()
                    .map(LinhaPreparacao::getMatricula)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .collect(Collectors.toSet());

            Map<String, Registration> existentes = registrationRepository.findByRegistrationIn(mats).stream()
                    .collect(Collectors.toMap(r -> r.getRegistration().toUpperCase(), r -> r, (a, b) -> a));

            Set<UUID> regIdsAlterados = new HashSet<>();

            for (LinhaPreparacao l : linhasAtivas) {
                if (l.getMatricula() == null || l.getMatricula().isBlank()) continue;
                String matUpper = l.getMatricula().trim().toUpperCase();
                Registration reg = existentes.get(matUpper);

                if (reg == null) {
                    registrosNovos++;
                    matriculasAtingidas.add(l.getMatricula().trim());
                    if (l.getCodLoja() != null) lojasAtingidas.add(l.getCodLoja());
                    if (l.getCodCargo() != null) cargosAtingidos.add(l.getCodCargo());
                } else {
                    boolean mudou = (l.getCodLoja() != null && (reg.getStore() == null || !Objects.equals(reg.getStore().getCode(), l.getCodLoja())))
                            || (l.getCodCargo() != null && (reg.getPosition() == null || !Objects.equals(reg.getPosition().getCode(), l.getCodCargo())))
                            || !Objects.equals(reg.getAdmissDate(), l.getDataAdmissao())
                            || !Objects.equals(reg.getDemissDate(), l.getDataDemissao());

                    if (mudou) {
                        registrosAtualizados++;
                        matriculasAtingidas.add(reg.getRegistration());
                        matriculasAlteradasRh.add(reg.getRegistration().toUpperCase());
                        regIdsAlterados.add(reg.getId());

                        if (l.getCodLoja() != null) lojasAtingidas.add(l.getCodLoja());
                        if (reg.getStore() != null && reg.getStore().getCode() != null) lojasAtingidas.add(reg.getStore().getCode());
                        if (l.getCodCargo() != null) cargosAtingidos.add(l.getCodCargo());
                        if (reg.getPosition() != null && reg.getPosition().getCode() != null) cargosAtingidos.add(reg.getPosition().getCode());
                    } else {
                        registrosInalterados++;
                    }
                }
            }

            if (!regIdsAlterados.isEmpty()) {
                List<Sale> vendasColaboradores = saleRepository.findByRegistrationIdIn(regIdsAlterados);
                for (Sale s : vendasColaboradores) {
                    idsVendasAlteradas.add(s.getId());
                    if (s.getSaleDate() != null) {
                        competenciasAtingidas.add(YearMonth.from(s.getSaleDate()).toString());
                    }
                    if (s.getStore() != null && s.getStore().getCode() != null) lojasAtingidas.add(s.getStore().getCode());
                    if (s.getBrand() != null && s.getBrand().getCode() != null) marcasAtingidas.add(s.getBrand().getCode());
                }
            }

        } else if ("SALES".equalsIgnoreCase(lote.getTipoBase())) {
            if (lote.getCompetencia() != null) {
                competenciasAtingidas.add(lote.getCompetencia());
            }

            Set<String> matriculasVendas = linhasAtivas.stream()
                    .map(LinhaPreparacao::getMatricula)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .collect(Collectors.toSet());

            Map<String, Registration> colaboradores = registrationRepository.findByRegistrationIn(matriculasVendas).stream()
                    .collect(Collectors.toMap(r -> r.getRegistration().toUpperCase(), r -> r, (a, b) -> a));

            Set<UUID> idsAlvo = linhasAtivas.stream()
                    .map(LinhaPreparacao::getRegistroAlvoId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, Sale> salesPorId = idsAlvo.isEmpty() ? Collections.emptyMap() :
                    saleRepository.findAllById(idsAlvo).stream().collect(Collectors.toMap(Sale::getId, s -> s));

            List<UUID> regIds = colaboradores.values().stream()
                    .map(Registration::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<UUID, List<Sale>> salesPorRegId = regIds.isEmpty() ? Collections.emptyMap() :
                    saleRepository.findByRegistrationIdIn(regIds).stream()
                            .filter(s -> s.getRegistration() != null && s.getRegistration().getId() != null)
                            .collect(Collectors.groupingBy(s -> s.getRegistration().getId()));

            for (LinhaPreparacao l : linhasAtivas) {
                if (l.getMatricula() == null || l.getDataReferencia() == null || l.getValorVenda() == null) continue;
                String mat = l.getMatricula().trim();
                Registration reg = colaboradores.get(mat.toUpperCase());

                if (reg == null) {
                    continue; // Linha inválida (advertência) não é persistida em tb_sales
                }

                if (l.getDataReferencia() != null) {
                    competenciasAtingidas.add(YearMonth.from(l.getDataReferencia()).toString());
                }
                matriculasAtingidas.add(mat);
                if (l.getCodLoja() != null) lojasAtingidas.add(l.getCodLoja());
                if (l.getCodMarca() != null) marcasAtingidas.add(l.getCodMarca());
                if (reg.getPosition() != null && reg.getPosition().getCode() != null) cargosAtingidos.add(reg.getPosition().getCode());

                Sale saleAlvo = null;
                if (l.getRegistroAlvoId() != null) {
                    saleAlvo = salesPorId.get(l.getRegistroAlvoId());
                } else if (reg != null && l.getDataReferencia() != null) {
                    List<Sale> existentes = salesPorRegId.getOrDefault(reg.getId(), Collections.emptyList());
                    saleAlvo = existentes.stream()
                            .filter(s -> Objects.equals(s.getSaleDate(), l.getDataReferencia())
                                    && s.getStore() != null && Objects.equals(s.getStore().getCode(), l.getCodLoja())
                                    && s.getBrand() != null && Objects.equals(s.getBrand().getCode(), l.getCodMarca())
                                    && s.getValue() != null && l.getValorVenda() != null && s.getValue().compareTo(l.getValorVenda()) == 0)
                            .findFirst().orElse(null);
                }

                if (saleAlvo != null) {
                    boolean mudou = saleAlvo.getValue().compareTo(l.getValorVenda()) != 0
                            || !Objects.equals(saleAlvo.getSaleDate(), l.getDataReferencia())
                            || (saleAlvo.getStore() != null && !Objects.equals(saleAlvo.getStore().getCode(), l.getCodLoja()))
                            || (saleAlvo.getBrand() != null && !Objects.equals(saleAlvo.getBrand().getCode(), l.getCodMarca()))
                            || (saleAlvo.getRegistration() != null && !saleAlvo.getRegistration().getRegistration().equalsIgnoreCase(mat));

                    if (mudou) {
                        registrosAtualizados++;
                        idsVendasAlteradas.add(saleAlvo.getId());
                        if (saleAlvo.getSaleDate() != null) {
                            competenciasAtingidas.add(YearMonth.from(saleAlvo.getSaleDate()).toString());
                        }
                    } else {
                        registrosInalterados++;
                    }
                } else {
                    registrosNovos++;
                }
            }
        }

        // Validação de Fechamento de Competência
        for (String comp : competenciasAtingidas) {
            if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(comp, StatusFechamento.CONCLUIDO)) {
                bloqueiosFechamento.add("A competência " + comp + " possui fechamento final concluído e bloqueia correções nas bases de dados.");
            } else if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(comp, StatusFechamento.PROCESSANDO)) {
                bloqueiosFechamento.add("A competência " + comp + " está em processo de fechamento final.");
            }
        }

        boolean houveMudancasReais = (registrosNovos > 0 || registrosAtualizados > 0);

        // Identificação de Campanhas e Regras Afetadas
        List<CampanhaAfetadaDTO> campanhasAfetadas = identificarCampanhasAfetadas(
                lote, competenciasAtingidas, matriculasAtingidas, lojasAtingidas, cargosAtingidos, marcasAtingidas, houveMudancasReais
        );

        // Identificação de Simulações e Aceites Afetados
        List<SimulacaoAfetadaDTO> simulacoesAfetadas = identificarSimulacoesAfetadas(
                lote, idsVendasAlteradas, matriculasAlteradasRh, competenciasAtingidas, campanhasAfetadas
        );

        // Identificação de Cálculos Afetados
        List<CalculoAfetadoDTO> calculosAfetados = identificarCalculosAfetados(
                idsVendasAlteradas, matriculasAlteradasRh, competenciasAtingidas
        );

        // Identificação de Aprovações de Código Afetadas
        List<AprovacaoCodigoAfetadaDTO> aprovacoesCodigoAfetadas = identificarAprovacoesCodigoAfetadas(lote, campanhasAfetadas);

        boolean possuiImpactos = !campanhasAfetadas.isEmpty() || !simulacoesAfetadas.isEmpty()
                || !calculosAfetados.isEmpty() || !aprovacoesCodigoAfetadas.isEmpty();

        boolean exigeConfirmacaoExplicita = possuiImpactos;

        ResumoAlteracoesBaseDTO resumoAlteracoes = new ResumoAlteracoesBaseDTO(
                registrosNovos,
                registrosAtualizados,
                registrosInalterados,
                new ArrayList<>(competenciasAtingidas),
                new ArrayList<>(matriculasAtingidas),
                new ArrayList<>(lojasAtingidas),
                new ArrayList<>(cargosAtingidos),
                new ArrayList<>(marcasAtingidas)
        );

        String mensagem;
        if (!bloqueiosFechamento.isEmpty()) {
            mensagem = "Efetivação impedida por competência bloqueada por fechamento.";
        } else if (possuiImpactos) {
            mensagem = String.format("A correção atinge %d campanha(s), %d simulação(ões), %d competência(s) calculada(s) e %d aprovação(ões) de código. Exige confirmação explícita.",
                    campanhasAfetadas.size(), simulacoesAfetadas.size(), calculosAfetados.size(), aprovacoesCodigoAfetadas.size());
        } else {
            mensagem = "Nenhum impacto colateral detectado sobre simulações ou cálculos existentes. A efetivação pode prosseguir diretamente.";
        }

        return new PreviaImpactoCorrecaoDTO(
                lote.getId(),
                lote.getCampanha().getId(),
                lote.getTipoBase(),
                lote.getCompetencia(),
                possuiImpactos,
                exigeConfirmacaoExplicita,
                !bloqueiosFechamento.isEmpty(),
                bloqueiosFechamento,
                resumoAlteracoes,
                campanhasAfetadas,
                simulacoesAfetadas,
                calculosAfetados,
                aprovacoesCodigoAfetadas,
                mensagem
        );
    }

    /**
     * Executa a propagação transacional e atômica dos impactos confirmados.
     */
    @Transactional
    public ResumoPropagacaoImpactoDTO propagarImpactos(
            LotePreparacao lote,
            PreviaImpactoCorrecaoDTO previa,
            String autor,
            String motivoConfirmacao) {

        OffsetDateTime agora = OffsetDateTime.now();
        Long campanhaOrigemId = lote.getCampanha().getId();

        int qtdSimulacoesInvalidadas = 0;
        int qtdCalculosDesatualizados = 0;
        int qtdAprovacoesCodigoInvalidadas = 0;
        int qtdRegrasAfetadas = 0;
        Set<Long> campanhasIdsAfetados = new HashSet<>();

        // 1. Invalidar Simulações e Aceites Afetados
        for (SimulacaoAfetadaDTO simDTO : previa.simulacoesAfetadas()) {
            Optional<Simulacao> simOpt = simulacaoRepository.findById(simDTO.simulacaoId());
            if (simOpt.isPresent()) {
                Simulacao sim = simOpt.get();
                if (sim.getStatusAceite() != StatusAceiteSimulacao.INVALIDADA) {
                    sim.setStatusAceite(StatusAceiteSimulacao.INVALIDADA);
                    sim.setInvalidadoEm(agora);
                    sim.setMotivoInvalidacao(String.format("Invalidada pela confirmação de correção na base %s (lote %s) por %s. Motivo: %s",
                            lote.getTipoBase(), lote.getId(), autor, simDTO.motivoImpacto()));
                    simulacaoRepository.save(sim);
                    qtdSimulacoesInvalidadas++;

                    Campanha c = sim.getCampanha();
                    if (c != null && c.getSimulacaoAceita() != null && c.getSimulacaoAceita().getId().equals(sim.getId())) {
                        c.setSimulacaoAceita(null);
                        c.setEncaminhadaGerente(false);
                        c.setEncaminhadaGerentePor(null);
                        c.setEncaminhadaGerenteEm(null);
                        campanhaRepository.save(c);
                    }
                    CampanhaVersao v = sim.getCampanhaVersao();
                    if (v != null && v.getSimulacaoAceita() != null && v.getSimulacaoAceita().getId().equals(sim.getId())) {
                        v.setSimulacaoAceita(null);
                        v.setEncaminhadaGerente(false);
                        v.setEncaminhadaGerentePor(null);
                        v.setEncaminhadaGerenteEm(null);
                        campanhaVersaoRepository.save(v);
                    }
                }
            }
        }

        // 2. Preservar Snapshots de Resultados de Cálculo e Marcar Desatualização
        for (CalculoAfetadoDTO calcDTO : previa.calculosAfetados()) {
            if (!calcDTO.idsVendasAfetadas().isEmpty()) {
                List<ResultadoCalculo> resultados = resultadoCalculoRepository.findByIdVendaInAndFechamentoIdIsNull(calcDTO.idsVendasAfetadas());
                for (ResultadoCalculo r : resultados) {
                    // Preserva snapshot
                    SnapshotResultadoCalculo snapshot = new SnapshotResultadoCalculo(
                            r,
                            "Preservado antes de desatualização por correção na base " + lote.getTipoBase() + " (lote " + lote.getId() + ")",
                            lote
                    );
                    snapshotResultadoCalculoRepository.save(snapshot);

                    r.setDesatualizadoPorCorrecao(true);
                    r.setDesatualizadoEm(agora);
                    r.setMotivoDesatualizacao(String.format("Base %s corrigida pelo lote %s por %s. Nova execução requerida.",
                            lote.getTipoBase(), lote.getId(), autor));
                    r.setLoteCorrecao(lote);
                    resultadoCalculoRepository.save(r);
                    qtdCalculosDesatualizados++;
                }
            }
        }

        // 3. Invalidar Aprovações de Código de Regras e Versões Atingidas
        for (AprovacaoCodigoAfetadaDTO apDTO : previa.aprovacoesCodigoAfetadas()) {
            Optional<Regra> rOpt = regraRepository.findById(apDTO.regraId());
            if (rOpt.isPresent()) {
                Regra r = rOpt.get();
                r.setStatusAprovacaoCodigo(StatusAprovacaoCodigo.INVALIDADO);
                r.setCodigoInvalidadoEm(agora);
                r.setMotivoInvalidacaoCodigo("Aprovação de código invalidada por correção na base " + lote.getTipoBase() + " (lote " + lote.getId() + ")");
                regraRepository.save(r);
                qtdAprovacoesCodigoInvalidadas++;

                CampanhaVersao cv = r.getCampanhaVersao();
                if (cv != null && cv.getStatusAprovacaoCodigo() == StatusAprovacaoCodigo.APROVADO) {
                    cv.setStatusAprovacaoCodigo(StatusAprovacaoCodigo.INVALIDADO);
                    cv.setCodigoInvalidadoEm(agora);
                    cv.setMotivoInvalidacaoCodigo("Aprovação de código da versão invalidada por correção de base afetando regras.");
                    campanhaVersaoRepository.save(cv);
                }
            }
        }

        // Contabilizar campanhas e regras únicas
        for (CampanhaAfetadaDTO cDTO : previa.campanhasAfetadas()) {
            campanhasIdsAfetados.add(cDTO.campanhaId());
            qtdRegrasAfetadas += cDTO.regrasAtingidas().size();
        }

        // 4. Registro de Auditoria em tb_propagacao_impacto_base
        String detalhesJson = null;
        try {
            detalhesJson = JSON.writeValueAsString(previa);
        } catch (Exception e) {
            log.warn("Falha ao serializar detalhes da prévia de impacto para auditoria: {}", e.getMessage());
        }

        String compStr = String.join(", ", previa.resumoAlteracoesBase().competenciasAtingidas());
        if (compStr.length() > 250) {
            compStr = compStr.substring(0, 250);
        }

        PropagacaoImpactoBase auditoria = new PropagacaoImpactoBase(
                lote,
                lote.getCampanha(),
                lote.getTipoBase(),
                compStr,
                campanhasIdsAfetados.size(),
                qtdRegrasAfetadas,
                qtdSimulacoesInvalidadas,
                qtdCalculosDesatualizados,
                qtdAprovacoesCodigoInvalidadas,
                detalhesJson,
                autor,
                motivoConfirmacao
        );
        auditoria = propagacaoImpactoBaseRepository.save(auditoria);

        log.info("Propagação de impacto do lote {} confirmada por {}: {} campanhas, {} regras, {} simulações invalidadas, {} cálculos desatualizados, {} aprovações de código invalidadas.",
                lote.getId(), autor, campanhasIdsAfetados.size(), qtdRegrasAfetadas, qtdSimulacoesInvalidadas, qtdCalculosDesatualizados, qtdAprovacoesCodigoInvalidadas);

        return new ResumoPropagacaoImpactoDTO(
                auditoria.getId(),
                campanhasIdsAfetados.size(),
                qtdRegrasAfetadas,
                qtdSimulacoesInvalidadas,
                qtdCalculosDesatualizados,
                qtdAprovacoesCodigoInvalidadas,
                previa.resumoAlteracoesBase().competenciasAtingidas(),
                agora,
                autor,
                "Impactos propagados com sucesso para campanhas, simulações e cálculos dependentes."
        );
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE ANÁLISE DE IMPACTO
    // =========================================================================

    private List<CampanhaAfetadaDTO> identificarCampanhasAfetadas(
            LotePreparacao lote,
            Set<String> competenciasAtingidas,
            Set<String> matriculasAtingidas,
            Set<Integer> lojasAtingidas,
            Set<Integer> cargosAtingidos,
            Set<Integer> marcasAtingidas,
            boolean houveMudancasReais) {

        List<CampanhaAfetadaDTO> afetadas = new ArrayList<>();
        List<Campanha> todasCampanhas = campanhaRepository.findAllByRemovidoEmIsNullOrderByCriadoEmDesc();

        for (Campanha c : todasCampanhas) {
            if (c.getEstado() == EstadoCampanha.CANCELADA || c.getEstado() == EstadoCampanha.CONCLUIDA) {
                continue;
            }

            CampanhaVersao versao = c.getVersaoProposta() != null ? c.getVersaoProposta() : c.getVersaoVigente();
            List<Regra> regras = versao != null
                    ? regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId())
                    : regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(c.getId());

            List<RegraAfetadaDTO> regrasAtingidas = new ArrayList<>();

            for (Regra r : regras) {
                // Interceptação de período
                boolean periodoCasou = periodoIntercepta(r.getDataInicio(), r.getDataFim(), competenciasAtingidas);



                if (!periodoCasou) continue;

                // Casamento de filtros de público da regra
                String motivo = verificarCasamentoRegra(r, matriculasAtingidas, lojasAtingidas, cargosAtingidos, marcasAtingidas);
                if (motivo != null) {
                    regrasAtingidas.add(new RegraAfetadaDTO(
                            r.getId(),
                            r.getNome(),
                            r.getStatus(),
                            r.getStatusAprovacaoCodigo(),
                            motivo
                    ));
                }
            }

            boolean campanhaOrigemAfetada = c.getId().equals(lote.getCampanha().getId())
                    && houveMudancasReais
                    && !competenciasAtingidas.isEmpty();
            if (!regrasAtingidas.isEmpty() || campanhaOrigemAfetada) {
                boolean simAceita = c.getSimulacaoAceita() != null;
                boolean codAprovado = (versao != null && versao.getStatusAprovacaoCodigo() == StatusAprovacaoCodigo.APROVADO)
                        || regrasAtingidas.stream().anyMatch(r -> r.statusAprovacaoCodigoAtual() == StatusAprovacaoCodigo.APROVADO);

                afetadas.add(new CampanhaAfetadaDTO(
                        c.getId(),
                        c.getTitulo(),
                        c.getEstado(),
                        versao != null ? versao.getNumero() : 1,
                        versao != null ? versao.getStatus() : StatusVersaoCampanha.PROPOSTA,
                        simAceita,
                        codAprovado,
                        regrasAtingidas
                ));
            }
        }

        return afetadas;
    }

    private String verificarCasamentoRegra(
            Regra r,
            Set<String> matriculas,
            Set<Integer> lojas,
            Set<Integer> cargos,
            Set<Integer> marcas) {

        List<String> motivos = new ArrayList<>();

        if (r.getMatricula() != null && !r.getMatricula().isBlank()) {
            if (matriculas.contains(r.getMatricula().trim())) {
                motivos.add("Matrícula individual da regra [" + r.getMatricula() + "] foi alterada/inserida.");
            } else {
                return null; // Filtro estrito de matrícula que não casou
            }
        }

        if (r.getCodLoja() != null) {
            if (lojas.contains(r.getCodLoja())) {
                motivos.add("Loja da regra [" + r.getCodLoja() + "] atingida.");
            } else {
                return null;
            }
        }

        if (r.getCodCargo() != null) {
            if (cargos.contains(r.getCodCargo())) {
                motivos.add("Cargo da regra [" + r.getCodCargo() + "] atingido.");
            } else {
                return null;
            }
        }

        if (r.getCodMarca() != null) {
            if (marcas.contains(r.getCodMarca())) {
                motivos.add("Marca da regra [" + r.getCodMarca() + "] atingida.");
            } else {
                return null;
            }
        }

        if (!motivos.isEmpty()) {
            return String.join(" ", motivos);
        }

        // Se a regra não restringe dimensão nenhuma (regra geral), qualquer venda inserida/alterada no período a atinge
        if (!matriculas.isEmpty() || !lojas.isEmpty() || !cargos.isEmpty() || !marcas.isEmpty()) {
            return "Regra geral sem restrições específicas cobre registros alterados no período.";
        }

        return null;
    }

    private boolean periodoIntercepta(LocalDate inicio, LocalDate fim, Set<String> competencias) {
        if (inicio == null || fim == null || competencias.isEmpty()) return false;
        YearMonth inicioYm = YearMonth.from(inicio);
        YearMonth fimYm = YearMonth.from(fim);

        for (String comp : competencias) {
            try {
                YearMonth ym = YearMonth.parse(comp);
                if (!ym.isBefore(inicioYm) && !ym.isAfter(fimYm)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private List<SimulacaoAfetadaDTO> identificarSimulacoesAfetadas(
            LotePreparacao lote,
            Set<UUID> idsVendasAlteradas,
            Set<String> matriculasAlteradasRh,
            Set<String> competenciasAtingidas,
            List<CampanhaAfetadaDTO> campanhasAfetadas) {

        Map<UUID, SimulacaoAfetadaDTO> afetadas = new LinkedHashMap<>();

        // 1. Simulações que contêm vendas alteradas
        if (!idsVendasAlteradas.isEmpty()) {
            List<SimulacaoVenda> svs = simulacaoVendaRepository.findByIdVendaIn(idsVendasAlteradas);
            Map<UUID, List<SimulacaoVenda>> porSim = svs.stream().collect(Collectors.groupingBy(s -> s.getSimulacao().getId()));
            for (Map.Entry<UUID, List<SimulacaoVenda>> entry : porSim.entrySet()) {
                Simulacao sim = entry.getValue().get(0).getSimulacao();
                if (sim.getStatusAceite() != StatusAceiteSimulacao.INVALIDADA) {
                    afetadas.put(sim.getId(), new SimulacaoAfetadaDTO(
                            sim.getId(),
                            sim.getProtocolo(),
                            sim.getCampanha() != null ? sim.getCampanha().getId() : null,
                            sim.getCampanha() != null ? sim.getCampanha().getTitulo() : "Sem Campanha",
                            sim.getCompetencia(),
                            sim.getStatusAceite(),
                            entry.getValue().size(),
                            "INVALIDAR_SIMULACAO_E_ACEITE",
                            String.format("%d venda(s) simuladas foram alteradas na base.", entry.getValue().size())
                    ));
                }
            }
        }

        // 2. Simulações que contêm matrículas alteradas no RH
        if (!matriculasAlteradasRh.isEmpty()) {
            List<SimulacaoVenda> svs = simulacaoVendaRepository.findByMatriculaIn(matriculasAlteradasRh);
            Map<UUID, List<SimulacaoVenda>> porSim = svs.stream().collect(Collectors.groupingBy(s -> s.getSimulacao().getId()));
            for (Map.Entry<UUID, List<SimulacaoVenda>> entry : porSim.entrySet()) {
                Simulacao sim = entry.getValue().get(0).getSimulacao();
                if (sim.getStatusAceite() != StatusAceiteSimulacao.INVALIDADA && !afetadas.containsKey(sim.getId())) {
                    afetadas.put(sim.getId(), new SimulacaoAfetadaDTO(
                            sim.getId(),
                            sim.getProtocolo(),
                            sim.getCampanha() != null ? sim.getCampanha().getId() : null,
                            sim.getCampanha() != null ? sim.getCampanha().getTitulo() : "Sem Campanha",
                            sim.getCompetencia(),
                            sim.getStatusAceite(),
                            entry.getValue().size(),
                            "INVALIDAR_SIMULACAO_E_ACEITE",
                            String.format("Colaboradores de %d venda(s) simuladas tiveram cadastro alterado no RH.", entry.getValue().size())
                    ));
                }
            }
        }

        // 3. Simulações ativas pertencentes às campanhas/versões com regras atingidas nas competências
        Set<Long> campIds = campanhasAfetadas.stream().map(CampanhaAfetadaDTO::campanhaId).collect(Collectors.toSet());
        if (!campIds.isEmpty() && !competenciasAtingidas.isEmpty()) {
            List<Simulacao> sims = simulacaoRepository.findByCampanhaIdInAndStatusAceiteIn(
                    campIds, List.of(StatusAceiteSimulacao.ACEITA, StatusAceiteSimulacao.PENDENTE)
            );
            for (Simulacao sim : sims) {
                if (competenciasAtingidas.contains(sim.getCompetencia()) && !afetadas.containsKey(sim.getId())) {
                    afetadas.put(sim.getId(), new SimulacaoAfetadaDTO(
                            sim.getId(),
                            sim.getProtocolo(),
                            sim.getCampanha() != null ? sim.getCampanha().getId() : null,
                            sim.getCampanha() != null ? sim.getCampanha().getTitulo() : "Sem Campanha",
                            sim.getCompetencia(),
                            sim.getStatusAceite(),
                            0,
                            "INVALIDAR_SIMULACAO_E_ACEITE",
                            "Novos registros ou alterações atingem o público das regras desta simulação na competência " + sim.getCompetencia()
                    ));
                }
            }
        }

        return new ArrayList<>(afetadas.values());
    }

    private List<CalculoAfetadoDTO> identificarCalculosAfetados(
            Set<UUID> idsVendasAlteradas,
            Set<String> matriculasAlteradasRh,
            Set<String> competenciasAtingidas) {

        Map<String, Set<UUID>> resultadosPorComp = new LinkedHashMap<>();
        Map<String, Set<UUID>> vendasPorComp = new LinkedHashMap<>();

        if (!idsVendasAlteradas.isEmpty()) {
            List<ResultadoCalculo> res = resultadoCalculoRepository.findByIdVendaInAndFechamentoIdIsNull(idsVendasAlteradas);
            for (ResultadoCalculo r : res) {
                String comp = YearMonth.from(r.getDataVenda()).toString();
                resultadosPorComp.computeIfAbsent(comp, k -> new HashSet<>()).add(r.getProtocoloCalculo());
                vendasPorComp.computeIfAbsent(comp, k -> new HashSet<>()).add(r.getIdVenda());
            }
        }

        if (!matriculasAlteradasRh.isEmpty()) {
            List<ResultadoCalculo> res = resultadoCalculoRepository.findByMatriculaInAndFechamentoIdIsNull(matriculasAlteradasRh);
            for (ResultadoCalculo r : res) {
                String comp = YearMonth.from(r.getDataVenda()).toString();
                resultadosPorComp.computeIfAbsent(comp, k -> new HashSet<>()).add(r.getProtocoloCalculo());
                vendasPorComp.computeIfAbsent(comp, k -> new HashSet<>()).add(r.getIdVenda());
            }
        }

        List<CalculoAfetadoDTO> dtos = new ArrayList<>();
        for (String comp : competenciasAtingidas) {
            Set<UUID> protocolos = resultadosPorComp.getOrDefault(comp, Set.of());
            Set<UUID> vendas = vendasPorComp.getOrDefault(comp, Set.of());

            List<UUID> execConcluidas = execucaoCalculoRepository.findByCompetenciaOrderBySolicitadoEmDesc(comp).stream()
                    .filter(e -> e.getStatus() == StatusExecucaoCalculo.CONCLUIDA)
                    .map(ExecucaoCalculo::getId)
                    .toList();

            if (!protocolos.isEmpty() || !execConcluidas.isEmpty()) {
                dtos.add(new CalculoAfetadoDTO(
                        comp,
                        protocolos.size(),
                        new ArrayList<>(protocolos),
                        new ArrayList<>(vendas),
                        execConcluidas,
                        "PRESERVAR_SNAPSHOT_E_EXIGIR_NOVA_EXECUCAO",
                        String.format("%d resultado(s) emitido(s) e %d execução(ões) assíncrona(s) serão superadas por esta correção na competência %s.",
                                protocolos.size(), execConcluidas.size(), comp)
                ));
            }
        }

        return dtos;
    }

    private List<AprovacaoCodigoAfetadaDTO> identificarAprovacoesCodigoAfetadas(LotePreparacao lote, List<CampanhaAfetadaDTO> campanhasAfetadas) {
        List<AprovacaoCodigoAfetadaDTO> aprovacoes = new ArrayList<>();

        for (CampanhaAfetadaDTO c : campanhasAfetadas) {
            for (RegraAfetadaDTO r : c.regrasAtingidas()) {
                if (r.statusAprovacaoCodigoAtual() == StatusAprovacaoCodigo.APROVADO) {
                    aprovacoes.add(new AprovacaoCodigoAfetadaDTO(
                            c.campanhaId(),
                            c.versaoNumero(),
                            r.regraId(),
                            r.nomeRegra(),
                            r.statusAprovacaoCodigoAtual(),
                            "INVALIDAR_APROVACAO_CODIGO",
                            "Artefatos ou dados dependentes da regra sofreram correção de base: " + r.motivoImpacto()
                    ));
                }
            }
        }

        return aprovacoes;
    }
}
