package com.bugbusters.backend.consultasia;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.consultasia.RecortePopulacaoIaDTOs.*;
import com.bugbusters.backend.dto.basecomiss.ReferenciaFaltanteDTO;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO;
import com.bugbusters.backend.dto.interpretador.proposta.*;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.RecortePopulacaoIncompletoException;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.ArtefatoExplicativoService;
import com.bugbusters.backend.service.CoberturaBaseService;
import com.bugbusters.backend.service.ReferenciaPublicoService;
import com.bugbusters.backend.service.TaxaComissaoResolver;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import com.bugbusters.backend.service.pendencia.AceitePendenciaProvider;
import com.bugbusters.backend.service.pendencia.StatusAceitePendenciaDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável por gerar e validar o recorte completo, reproduzível e versionado
 * da população selecionada para alimentar as tarefas de IA (BACK-10 / IA-3 a IA-6).
 */
@Service
public class RecortePopulacaoIaService {

    private static final Logger log = LoggerFactory.getLogger(RecortePopulacaoIaService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BigDecimal DIAS_BASE_MES = new BigDecimal("30");

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;
    private final RegraRepository regraRepository;
    private final SaleRepository saleRepository;
    private final RegistrationRepository registrationRepository;
    private final BaseComissRepository baseComissRepository;
    private final EnvioArquivoRepository envioArquivoRepository;
    private final InconsistenciaImportacaoRepository inconsistenciaRepository;
    private final CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;
    private final CoberturaBaseService coberturaBaseService;
    private final ReferenciaPublicoService referenciaPublicoService;
    private final TravaCompetencia travaCompetencia;
    private final FechamentoCompetenciaRepository fechamentoCompetenciaRepository;
    private final AceitePendenciaProvider aceitePendenciaProvider;
    private final ArtefatoExplicativoService artefatoService;

    public RecortePopulacaoIaService(
            CampanhaRepository campanhaRepository,
            CampanhaVersaoRepository campanhaVersaoRepository,
            RegraRepository regraRepository,
            SaleRepository saleRepository,
            RegistrationRepository registrationRepository,
            BaseComissRepository baseComissRepository,
            EnvioArquivoRepository envioArquivoRepository,
            InconsistenciaImportacaoRepository inconsistenciaRepository,
            CampanhaAnexoBaseRepository campanhaAnexoBaseRepository,
            CoberturaBaseService coberturaBaseService,
            ReferenciaPublicoService referenciaPublicoService,
            TravaCompetencia travaCompetencia,
            FechamentoCompetenciaRepository fechamentoCompetenciaRepository,
            AceitePendenciaProvider aceitePendenciaProvider,
            ArtefatoExplicativoService artefatoService) {
        this.campanhaRepository = campanhaRepository;
        this.campanhaVersaoRepository = campanhaVersaoRepository;
        this.regraRepository = regraRepository;
        this.saleRepository = saleRepository;
        this.registrationRepository = registrationRepository;
        this.baseComissRepository = baseComissRepository;
        this.envioArquivoRepository = envioArquivoRepository;
        this.inconsistenciaRepository = inconsistenciaRepository;
        this.campanhaAnexoBaseRepository = campanhaAnexoBaseRepository;
        this.coberturaBaseService = coberturaBaseService;
        this.referenciaPublicoService = referenciaPublicoService;
        this.travaCompetencia = travaCompetencia;
        this.fechamentoCompetenciaRepository = fechamentoCompetenciaRepository;
        this.aceitePendenciaProvider = aceitePendenciaProvider;
        this.artefatoService = artefatoService != null ? artefatoService : new ArtefatoExplicativoService();
    }

    /**
     * Monta o recorte completo e reproduzível sob trava de competência,
     * aplicando validação estrita (Zero-Trust) para evitar recortes silenciosamente incompletos.
     */
    @Transactional
    public RecortePopulacaoIaResponse obterRecorte(RecortePopulacaoIaRequest request) {
        return montarRecorte(request);
    }

    @Transactional
    public RecortePopulacaoIaResponse montarRecorte(RecortePopulacaoIaRequest request) {
        if (request == null) {
            throw new BusinessException("A requisição de recorte de população não pode ser nula.");
        }
        if (request.campanhaId() == null && (request.regras() == null || request.regras().isEmpty())) {
            throw new BusinessException("É obrigatório informar 'campanhaId' ou ao menos uma regra em 'regras' para delimitar a população.");
        }

        // 1. Resolução da Campanha e Versão
        Campanha campanha = null;
        CampanhaVersao versao = null;
        if (request.campanhaId() != null) {
            campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(request.campanhaId())
                    .or(() -> campanhaRepository.findById(request.campanhaId()).filter(c -> c.getRemovidoEm() == null))
                    .orElseThrow(() -> new ResourceNotFoundException("Campanha não encontrada com ID: " + request.campanhaId()));

            if (request.versaoCampanhaEsperada() != null) {
                int versaoAtual = campanha.versaoParaRevisao() != null ? campanha.versaoParaRevisao().getNumero() : 1;
                if (!request.versaoCampanhaEsperada().equals(versaoAtual)) {
                    throw new ResourceConflictException(String.format(
                            "A campanha '%s' (ID %d) está na versão %d, mas a requisição esperava a versão %d. Recarregue os dados antes de prosseguir.",
                            campanha.getTitulo(), campanha.getId(), versaoAtual, request.versaoCampanhaEsperada()
                    ));
                }
            }

            if (request.versaoCampanhaNumero() != null) {
                versao = campanhaVersaoRepository.findByCampanhaIdAndNumero(campanha.getId(), request.versaoCampanhaNumero())
                        .orElseThrow(() -> new ResourceNotFoundException(String.format(
                                "Versão %d da campanha ID %d não encontrada.", request.versaoCampanhaNumero(), request.campanhaId())));
            } else if (Boolean.TRUE.equals(request.usarVersaoVigente())) {
                versao = campanha.getVersaoVigente();
                if (versao == null) {
                    throw new BusinessException("A campanha ID " + request.campanhaId() + " não possui versão vigente publicada.");
                }
            } else {
                versao = campanha.versaoParaRevisao();
            }
        }

        // 2. Resolução de Regras Avaliadas
        List<RegraItemRequest> regrasAvaliadas = resolverRegras(request, campanha, versao);
        if (regrasAvaliadas.isEmpty()) {
            throw new RecortePopulacaoIncompletoException(
                    "Nenhuma regra identificada para o recorte de população.",
                    List.of(new CampoInvalidoDTO("regras", "A campanha ou requisição não contém regras ativas ou propostas."))
            );
        }

        // 3. Determinação do Período de Vigência e Competências Abrangidas
        PeriodoCalculado periodo = calcularPeriodo(request, campanha, versao);

        // 4. Aquisição de Travas em Ordem Lexicográfica e Verificação de Fechamento
        for (String comp : periodo.competencias()) {
            if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(comp, StatusFechamento.PROCESSANDO)) {
                throw new BusinessException("A competência " + comp + " está em processo de fechamento final. Operação bloqueada temporariamente.");
            }
            travaCompetencia.adquirirTrava(comp);
        }

        // 5. Carregamento de Vendas do Período
        List<Sale> vendasPeriodo = saleRepository.findVendasPorPeriodoComRelacionamentos(
                periodo.dataInicioRecorte(), periodo.dataFimRecorte()
        );
        int totalVendasPeriodoBase = vendasPeriodo.size();
        BigDecimal valorTotalPeriodoBase = vendasPeriodo.stream()
                .map(s -> s.getValue() != null ? s.getValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // 6. Recorte pela União de Públicos (OU entre regras, E nos filtros) com Deduplicação por Sale.id
        List<Sale> vendasSelecionadas = new ArrayList<>();
        Map<UUID, List<RegraItemRequest>> regrasPorVenda = new LinkedHashMap<>();

        for (Sale sale : vendasPeriodo) {
            List<RegraItemRequest> regrasQueCobrem = new ArrayList<>();
            for (RegraItemRequest r : regrasAvaliadas) {
                if (referenciaPublicoService.atendePublico(r, sale)) {
                    regrasQueCobrem.add(r);
                }
            }
            if (!regrasQueCobrem.isEmpty()) {
                vendasSelecionadas.add(sale);
                regrasPorVenda.put(sale.getId(), regrasQueCobrem);
            }
        }

        // Ordenação determinística das vendas selecionadas
        vendasSelecionadas.sort(Comparator.comparing(Sale::getSaleDate).thenComparing(Sale::getId));

        // 7. Carregamento de Anexos da Campanha e Inconsistências / Pendências Aceitas
        List<CampanhaAnexoBase> anexosCampanha = (campanha != null)
                ? campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(campanha.getId())
                : Collections.emptyList();

        List<Long> envioIdsAnexados = anexosCampanha.stream()
                .map(a -> a.getEnvioArquivo().getId())
                .distinct()
                .toList();

        List<InconsistenciaImportacao> todasInconsistencias = envioIdsAnexados.isEmpty()
                ? Collections.emptyList()
                : inconsistenciaRepository.findByEnvioIdIn(envioIdsAnexados);

        List<PendenciaAceitaRecorteIaDTO> pendenciasAceitas = new ArrayList<>();
        List<CampoInvalidoDTO> pendenciasNaoAceitasImpedimentos = new ArrayList<>();

        for (InconsistenciaImportacao inc : todasInconsistencias) {
            String compInc = inc.getEnvio().getCompetencia();
            // Avalia apenas inconsistências gerais ou que pertençam às competências abrangidas pelo recorte
            if (compInc == null || periodo.competencias().contains(compInc)) {
                if ("IMPEDITIVO".equalsIgnoreCase(inc.getSeveridade())) {
                    pendenciasNaoAceitasImpedimentos.add(new CampoInvalidoDTO(
                            "lote:" + inc.getEnvio().getReportId() + ".linha:" + inc.getLinha(),
                            "Pendência IMPEDITIVA no lote " + inc.getEnvio().getReportId() + " (" + inc.getCampo() + "): " + inc.getMotivo()
                    ));
                } else {
                    StatusAceitePendenciaDTO aceite = (campanha != null)
                            ? aceitePendenciaProvider.consultarAceite(campanha.getId(), inc.getEnvio().getId(), inc)
                            : StatusAceitePendenciaDTO.naoAceita();

                    if (aceite.aceita()) {
                        pendenciasAceitas.add(new PendenciaAceitaRecorteIaDTO(
                                inc.getId(),
                                inc.getEnvio().getReportId(),
                                inc.getEnvio().getIdLote(),
                                inc.getEnvio().getTipoBase(),
                                inc.getEnvio().getCompetencia(),
                                inc.getLinha(),
                                inc.getCampo(),
                                inc.getMotivo(),
                                inc.getSeveridade(),
                                aceite.aceitaPor(),
                                aceite.aceitaEm(),
                                aceite.motivoAceite()
                        ));
                    } else {
                        pendenciasNaoAceitasImpedimentos.add(new CampoInvalidoDTO(
                                "lote:" + inc.getEnvio().getReportId() + ".linha:" + inc.getLinha(),
                                "Pendência sem aceite no lote " + inc.getEnvio().getReportId() + " (" + inc.getCampo() + "): " + inc.getMotivo()
                        ));
                    }
                }
            }
        }

        // 8. Validações Defensivas Estritas (Impedir Recortes Silenciosamente Incompletos)
        List<CampoInvalidoDTO> validacoesIncompletude = new ArrayList<>();

        if (totalVendasPeriodoBase == 0) {
            validacoesIncompletude.add(new CampoInvalidoDTO(
                    "periodo",
                    String.format("Não existem transações de vendas no período consultado (%s a %s).",
                            periodo.dataInicioRecorte(), periodo.dataFimRecorte())
            ));
        }

        if (vendasSelecionadas.isEmpty() && totalVendasPeriodoBase > 0) {
            validacoesIncompletude.add(new CampoInvalidoDTO(
                    "populacao",
                    String.format("Nenhuma venda no período atende ao público-alvo das regras avaliadas (total de vendas no mês: %d).", totalVendasPeriodoBase)
            ));
        }

        // Validação de pendências não aceitas em lotes anexados
        validacoesIncompletude.addAll(pendenciasNaoAceitasImpedimentos);

        // Validação detalhada de cada venda selecionada e cobertura de base contratual
        Map<String, Registration> mapaRh = new LinkedHashMap<>();
        Map<String, BaseComiss> mapaTaxas = new LinkedHashMap<>();

        List<ReferenciaFaltanteDTO> faltantes = new ArrayList<>();
        Set<YearMonth> mesesParaVerificar = new LinkedHashSet<>();
        if (periodo.competencias() != null && !periodo.competencias().isEmpty()) {
            for (String comp : periodo.competencias()) {
                try {
                    mesesParaVerificar.add(YearMonth.parse(comp));
                } catch (Exception ignored) {}
            }
        }
        if (mesesParaVerificar.isEmpty() && periodo.dataInicioRecorte() != null) {
            mesesParaVerificar.add(YearMonth.from(periodo.dataInicioRecorte()));
        }

        for (YearMonth ym : mesesParaVerificar) {
            List<Sale> vendasDoMes = vendasSelecionadas.stream()
                    .filter(s -> s.getSaleDate() != null && YearMonth.from(s.getSaleDate()).equals(ym))
                    .toList();
            if (!vendasDoMes.isEmpty()) {
                faltantes.addAll(coberturaBaseService.verificarCobertura(vendasDoMes, ym));
            } else if (vendasSelecionadas.isEmpty()) {
                faltantes.addAll(coberturaBaseService.verificarCobertura(Collections.emptyList(), ym));
            }
        }
        if (mesesParaVerificar.isEmpty()) {
            faltantes.addAll(coberturaBaseService.verificarCobertura(vendasSelecionadas, YearMonth.from(periodo.dataInicioRecorte())));
        }

        if (!faltantes.isEmpty()) {
            for (ReferenciaFaltanteDTO f : faltantes) {
                validacoesIncompletude.add(new CampoInvalidoDTO(
                        "baseComiss",
                        String.format("Taxa base contratual ausente em tb_basecomiss para Marca '%s' (%s) e Cargo '%s' (%s) na competência %s.",
                                f.descrMarca(), f.codMarca(), f.descriCargo(), f.codCargo(), f.competencia())
                ));
            }
        }

        for (Sale sale : vendasSelecionadas) {
            UUID idVenda = sale.getId();
            Registration reg = sale.getRegistration();
            Position pos = (reg != null) ? reg.getPosition() : null;
            Brand brand = sale.getBrand();

            if (reg == null || reg.getRegistration() == null || reg.getRegistration().isBlank()) {
                validacoesIncompletude.add(new CampoInvalidoDTO("venda:" + idVenda + ".matricula", "Venda sem colaborador/matrícula vinculada."));
            } else {
                mapaRh.putIfAbsent(reg.getRegistration().trim().toUpperCase(), reg);
            }

            if (pos == null || pos.getCode() == null) {
                validacoesIncompletude.add(new CampoInvalidoDTO("venda:" + idVenda + ".cargo", "Colaborador da venda sem cargo cadastrado no RH."));
            }

            if (sale.getStore() == null || sale.getStore().getCode() == null) {
                validacoesIncompletude.add(new CampoInvalidoDTO("venda:" + idVenda + ".loja", "Venda sem loja vinculada."));
            }

            if (brand == null || brand.getCode() == null) {
                validacoesIncompletude.add(new CampoInvalidoDTO("venda:" + idVenda + ".marca", "Venda sem marca vinculada."));
            }

            if (sale.getValue() == null || sale.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                validacoesIncompletude.add(new CampoInvalidoDTO("venda:" + idVenda + ".valor", "Valor da venda deve ser estritamente positivo (> 0)."));
            }

            // Vigência contratual de RH
            if (reg != null && sale.getSaleDate() != null) {
                boolean foraAdmissao = reg.getAdmissDate() != null && sale.getSaleDate().isBefore(reg.getAdmissDate());
                boolean foraDemissao = reg.getDemissDate() != null && sale.getSaleDate().isAfter(reg.getDemissDate());
                if (foraAdmissao || foraDemissao) {
                    // Verifica se há pendência aceita específica para essa matrícula
                    boolean possuiAceite = pendenciasAceitas.stream().anyMatch(p -> "matricula".equalsIgnoreCase(p.campo()));
                    if (!possuiAceite) {
                        validacoesIncompletude.add(new CampoInvalidoDTO(
                                "venda:" + idVenda + ".vigenciaContratual",
                                String.format("Data da venda (%s) fora da vigência do colaborador %s (admissão: %s, demissão: %s).",
                                        sale.getSaleDate(), reg.getRegistration(), reg.getAdmissDate(), reg.getDemissDate())
                        ));
                    }
                }
            }

            // Carrega taxa base correspondente
            if (brand != null && brand.getCode() != null && pos != null && pos.getCode() != null && sale.getSaleDate() != null) {
                YearMonth ym = YearMonth.from(sale.getSaleDate());
                String chaveTaxa = brand.getCode() + ":" + pos.getCode() + ":" + ym;
                if (!mapaTaxas.containsKey(chaveTaxa)) {
                    baseComissRepository.findByBrandCodeAndPositionCodeAndReferenceMonth(
                            brand.getCode(), pos.getCode(), ym.atDay(1)
                    ).ifPresent(bc -> mapaTaxas.put(chaveTaxa, bc));
                }
            }

            // Verificação de empate na maior especificidade entre regras aplicáveis
            List<RegraItemRequest> regrasCobrem = regrasPorVenda.getOrDefault(sale.getId(), Collections.emptyList());
            if (regrasCobrem.size() > 1 && sale.getValue() != null) {
                List<RegraItemRequest> aplicamFaixa = regrasCobrem.stream()
                        .filter(r -> referenciaPublicoService.atendeFaixaValor(r, sale.getValue()))
                        .toList();

                if (aplicamFaixa.size() > 1) {
                    int maxEsp = -1;
                    int countMax = 0;
                    List<String> nomesEmpatados = new ArrayList<>();
                    for (RegraItemRequest r : aplicamFaixa) {
                        int esp = referenciaPublicoService.calcularEspecificidade(r);
                        if (esp > maxEsp) {
                            maxEsp = esp;
                            countMax = 1;
                            nomesEmpatados.clear();
                            nomesEmpatados.add(r.nome());
                        } else if (esp == maxEsp) {
                            countMax++;
                            nomesEmpatados.add(r.nome());
                        }
                    }
                    if (countMax > 1) {
                        validacoesIncompletude.add(new CampoInvalidoDTO(
                                "venda:" + idVenda + ".conflitoRegras",
                                String.format("Conflito de regras: múltiplas regras empatadas na maior especificidade (peso %d) para a venda %s: %s.",
                                        maxEsp, idVenda, nomesEmpatados)
                        ));
                    }
                }
            }
        }

        // Se houver qualquer incompletude ou inconsistência, lança 422 imediatamente
        if (!validacoesIncompletude.isEmpty()) {
            throw new RecortePopulacaoIncompletoException(
                    "O recorte da população selecionada não pôde ser gerado porque há inconsistências ou ausência de dados necessários para cálculo reproduzível.",
                    validacoesIncompletude
            );
        }

        // 9. Montagem das Listas de Retorno DTO
        List<VendaRecorteIaDTO> vendasDTO = vendasSelecionadas.stream().map(s -> {
            Registration r = s.getRegistration();
            Position p = r != null ? r.getPosition() : null;
            Brand b = s.getBrand();
            Store st = s.getStore();
            return new VendaRecorteIaDTO(
                    s.getId(),
                    r != null ? r.getRegistration() : null,
                    st != null ? st.getCode() : null,
                    st != null ? st.getDescription() : null,
                    b != null ? b.getCode() : null,
                    b != null ? b.getDescription() : null,
                    p != null ? p.getCode() : null,
                    p != null ? p.getDescription() : null,
                    s.getValue(),
                    s.getSaleDate(),
                    YearMonth.from(s.getSaleDate()).toString(),
                    s.getSaleChannel() != null ? s.getSaleChannel() : "PADRAO",
                    s.getTipoVenda(),
                    s.getIdLoteOrigem(),
                    s.getVersao(),
                    s.getOrigemUltimaAlteracao(),
                    s.getIdLoteUltimaAlteracao(),
                    s.getAtualizadoEm()
            );
        }).toList();

        List<ColaboradorRhRecorteIaDTO> rhDTO = mapaRh.values().stream()
                .sorted(Comparator.comparing(Registration::getRegistration))
                .map(r -> {
                    Position p = r.getPosition();
                    Store st = r.getStore();
                    return new ColaboradorRhRecorteIaDTO(
                            r.getId(),
                            r.getRegistration(),
                            st != null ? st.getCode() : null,
                            st != null ? st.getDescription() : null,
                            p != null ? p.getCode() : null,
                            p != null ? p.getDescription() : null,
                            r.getAdmissDate(),
                            r.getDemissDate(),
                            r.getIdLoteOrigem(),
                            r.getVersao(),
                            r.getOrigemUltimaAlteracao(),
                            r.getIdLoteUltimaAlteracao(),
                            r.getAtualizadoEm()
                    );
                }).toList();

        List<TaxaContratualRecorteIaDTO> taxasDTO = mapaTaxas.values().stream()
                .sorted(Comparator.comparing(BaseComiss::getReferenceMonth)
                        .thenComparing(bc -> bc.getBrand().getCode())
                        .thenComparing(bc -> bc.getPosition().getCode()))
                .map(bc -> new TaxaContratualRecorteIaDTO(
                        bc.getId(),
                        bc.getBrand() != null ? bc.getBrand().getCode() : null,
                        bc.getBrand() != null ? bc.getBrand().getDescription() : null,
                        bc.getPosition() != null ? bc.getPosition().getCode() : null,
                        bc.getPosition() != null ? bc.getPosition().getDescription() : null,
                        YearMonth.from(bc.getReferenceMonth()).toString(),
                        bc.getReferenceMonth(),
                        bc.getPercentage(),
                        bc.getIdLoteOrigem()
                )).toList();

        Map<Long, Regra> mapaRegrasEntidade = (campanha != null)
                ? regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId()).stream()
                    .collect(Collectors.toMap(Regra::getId, rg -> rg, (a, b) -> a))
                : Collections.emptyMap();

        List<RegraRecorteIaDTO> regrasDTO = regrasAvaliadas.stream().map(r -> {
            int esp = referenciaPublicoService.calcularEspecificidade(r);
            PropostaRegraDTO propostaDTO = converterParaPropostaDTO(r, periodo.dataInicioCampanha(), periodo.dataFimCampanha());
            Long idRegra = extrairIdRegra(r.blocoId());
            Regra entidade = idRegra != null ? mapaRegrasEntidade.get(idRegra) : null;

            String python = (r.pythonEquivalente() != null && !r.pythonEquivalente().isBlank())
                    ? r.pythonEquivalente()
                    : (entidade != null && entidade.getPythonEquivalente() != null)
                        ? entidade.getPythonEquivalente()
                        : artefatoService.gerarPythonEquivalente(propostaDTO);

            String explicacao = (r.explicacao() != null && !r.explicacao().isBlank())
                    ? r.explicacao()
                    : (entidade != null && entidade.getExplicacao() != null)
                        ? entidade.getExplicacao()
                        : artefatoService.gerarExplicacao(propostaDTO);
            return new RegraRecorteIaDTO(
                    extrairIdRegra(r.blocoId()),
                    r.blocoId(),
                    r.nome(),
                    r.canal(),
                    r.codMarca(),
                    r.descrMarca(),
                    r.codLoja(),
                    r.codCargo(),
                    r.descriCargo(),
                    r.matricula(),
                    r.valorMinimo(),
                    r.minInclusivo(),
                    r.valorMaximo(),
                    r.maxInclusivo(),
                    r.tipoOperacao() != null ? r.tipoOperacao() : TipoOperacaoBase.DEFINIR_TAXA,
                    r.valorAjuste(),
                    r.taxaBaseConsultada(),
                    r.taxa(),
                    r.bonusPercentual(),
                    r.bonusValorFixo(),
                    esp,
                    periodo.dataInicioCampanha(),
                    periodo.dataFimCampanha(),
                    python,
                    explicacao
            );
        }).toList();

        // 10. Regras Concorrentes Ativas de Outras Campanhas
        List<RegraConcorrenteRecorteIaDTO> regrasConcorrentes = buscarRegrasConcorrentesAtivas(
                periodo.dataInicioRecorte(), periodo.dataFimRecorte(), campanha != null ? campanha.getId() : null, regrasAvaliadas
        );

        // 11. Coleta de Lotes de Importação / Preparação Envolvidos
        Set<String> lotesIds = new LinkedHashSet<>();
        vendasSelecionadas.forEach(s -> {
            if (s.getIdLoteOrigem() != null) lotesIds.add(s.getIdLoteOrigem());
            if (s.getIdLoteUltimaAlteracao() != null) lotesIds.add(s.getIdLoteUltimaAlteracao());
        });
        mapaRh.values().forEach(r -> {
            if (r.getIdLoteOrigem() != null) lotesIds.add(r.getIdLoteOrigem());
            if (r.getIdLoteUltimaAlteracao() != null) lotesIds.add(r.getIdLoteUltimaAlteracao());
        });
        taxasDTO.forEach(t -> {
            if (t.idLoteOrigem() != null) lotesIds.add(t.idLoteOrigem());
        });

        Set<Long> reportIdsAnexados = anexosCampanha.stream().map(a -> a.getEnvioArquivo().getReportId()).collect(Collectors.toSet());
        List<LoteFonteRecorteIaDTO> lotesFontesDTO = new ArrayList<>();

        for (String idLote : lotesIds) {
            envioArquivoRepository.findByIdLote(idLote).ifPresent(lote -> lotesFontesDTO.add(new LoteFonteRecorteIaDTO(
                    lote.getIdLote(),
                    lote.getReportId(),
                    lote.getTipoBase(),
                    lote.getCompetencia(),
                    lote.getNomeArquivo(),
                    lote.getHashConteudo(),
                    lote.getOrigemLote(),
                    lote.getEnvioAnteriorId(),
                    lote.getFinalizadoEm(),
                    reportIdsAnexados.contains(lote.getReportId())
            )));
        }

        // Adiciona lotes anexados que talvez não tenham vendas/RH associadas diretamente
        for (CampanhaAnexoBase anexo : anexosCampanha) {
            EnvioArquivo lote = anexo.getEnvioArquivo();
            if (lotesFontesDTO.stream().noneMatch(l -> Objects.equals(l.reportId(), lote.getReportId()))) {
                lotesFontesDTO.add(new LoteFonteRecorteIaDTO(
                        lote.getIdLote(),
                        lote.getReportId(),
                        lote.getTipoBase(),
                        lote.getCompetencia(),
                        lote.getNomeArquivo(),
                        lote.getHashConteudo(),
                        lote.getOrigemLote(),
                        lote.getEnvioAnteriorId(),
                        lote.getFinalizadoEm(),
                        true
                ));
            }
        }
        lotesFontesDTO.sort(Comparator.comparing(LoteFonteRecorteIaDTO::reportId, Comparator.nullsLast(Comparator.naturalOrder())));

        // 12. Estatísticas de Versões de Registros
        ResumoVersaoRegistrosDTO resumoVendas = calcularResumoVersoes(
                vendasSelecionadas.stream().map(s -> new RegistroVersao(s.getVersao(), s.getIdLoteOrigem(), s.getIdLoteUltimaAlteracao())).toList()
        );
        ResumoVersaoRegistrosDTO resumoRh = calcularResumoVersoes(
                mapaRh.values().stream().map(r -> new RegistroVersao(r.getVersao(), r.getIdLoteOrigem(), r.getIdLoteUltimaAlteracao())).toList()
        );

        // 13. Metadados da Campanha
        FonteCampanhaRecorteIaDTO fonteCampanha = (campanha != null)
                ? new FonteCampanhaRecorteIaDTO(
                        campanha.getId(),
                        campanha.getTitulo(),
                        campanha.getEstado().name(),
                        versao != null ? versao.getId() : null,
                        versao != null ? versao.getNumero() : null,
                        versao != null && versao.getStatus() != null ? versao.getStatus().name() : null,
                        campanha.getVersaoControle(),
                        versao != null && versao.getStatusAprovacaoCodigo() != null ? versao.getStatusAprovacaoCodigo().name() : null,
                        versao != null ? versao.getHashArtefatosCodigo() : null,
                        campanha.orcamentoParaRevisao(),
                        campanha.orcamentoConfirmadoParaRevisao(),
                        campanha.origemOrcamentoParaRevisao() != null ? campanha.origemOrcamentoParaRevisao().name() : null
                )
                : null;

        // 14. Cálculo Determinístico de Hashes Canônicos SHA-256
        String hashVendas = calcularHashVendas(vendasDTO);
        String hashRh = calcularHashRh(rhDTO);
        String hashTaxas = calcularHashTaxas(taxasDTO);
        String hashRegras = calcularHashRegras(regrasDTO);
        String hashPendencias = calcularHashPendencias(pendenciasAceitas);

        String hashRecorte = sha256(
                "recorte:v1",
                List.of(
                        row("vendas", hashVendas),
                        row("rh", hashRh),
                        row("taxas", hashTaxas),
                        row("regras", hashRegras),
                        row("pendencias", hashPendencias),
                        row("periodo", periodo.competenciaReferencia(), periodo.dataInicioRecorte(), periodo.dataFimRecorte())
                )
        );

        // Se o chamador informou hashRecorteEsperado e divergiu, lança 409 Conflict
        if (request.hashRecorteEsperado() != null && !request.hashRecorteEsperado().isBlank()) {
            if (!request.hashRecorteEsperado().trim().equalsIgnoreCase(hashRecorte)) {
                List<String> alteradas = new ArrayList<>();
                alteradas.add("RECORTE_DIVERGENTE");
                throw new ResourceConflictException(String.format(
                        "O hash canônico do recorte atual (%s) divergiu do hash esperado pela IA (%s). Fontes ou registros sofreram mutação.",
                        hashRecorte, request.hashRecorteEsperado().trim()
                ));
            }
        }

        VersoesFontesRecorteIaDTO versoesFontes = new VersoesFontesRecorteIaDTO(
                hashRecorte,
                hashVendas,
                hashRh,
                hashTaxas,
                hashRegras,
                hashPendencias,
                fonteCampanha,
                resumoVendas,
                resumoRh,
                lotesFontesDTO
        );

        BigDecimal valorTotalVendasSelecionadas = vendasSelecionadas.stream()
                .map(s -> s.getValue() != null ? s.getValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        TotaisRecorteIaDTO totais = new TotaisRecorteIaDTO(
                totalVendasPeriodoBase,
                valorTotalPeriodoBase,
                vendasSelecionadas.size(),
                valorTotalVendasSelecionadas,
                mapaRh.size(),
                taxasDTO.size(),
                regrasDTO.size(),
                regrasConcorrentes.size(),
                pendenciasAceitas.size(),
                lotesFontesDTO.size()
        );

        PeriodoRecorteIaDTO periodoDTO = new PeriodoRecorteIaDTO(
                periodo.competenciaReferencia(),
                periodo.competencias(),
                periodo.dataInicioCampanha(),
                periodo.dataFimCampanha(),
                periodo.dataInicioRecorte(),
                periodo.dataFimRecorte(),
                periodo.diasInclusivos(),
                periodo.fatorProjecao()
        );

        return new RecortePopulacaoIaResponse(
                UUID.randomUUID(),
                OffsetDateTime.now(),
                periodoDTO,
                versoesFontes,
                totais,
                regrasDTO,
                regrasConcorrentes,
                vendasDTO,
                rhDTO,
                taxasDTO,
                pendenciasAceitas
        );
    }

    /**
     * Valida rapidamente se as fontes do recorte mantêm os mesmos hashes canônicos
     * durante ou após a execução da IA, retornando diagnóstico das fontes alteradas.
     */
    @Transactional
    public ConsistenciaRecorteIaResponse verificarConsistencia(VerificarConsistenciaRecorteIaRequest request) {
        if (request == null) {
            throw new BusinessException("Requisição de conferência de consistência não pode ser nula.");
        }

        RecortePopulacaoIaRequest reqRecorte = new RecortePopulacaoIaRequest(
                request.campanhaId(), null, null, request.competencia(),
                null, null, null, null, null
        );

        RecortePopulacaoIaResponse recorteAtual = montarRecorte(reqRecorte);
        VersoesFontesRecorteIaDTO versoes = recorteAtual.versoesFontes();

        List<String> alteradas = new ArrayList<>();

        if (request.hashVendasEsperado() != null && !request.hashVendasEsperado().equalsIgnoreCase(versoes.hashVendas())) {
            alteradas.add("VENDAS");
        }
        if (request.hashRhEsperado() != null && !request.hashRhEsperado().equalsIgnoreCase(versoes.hashRh())) {
            alteradas.add("RH");
        }
        if (request.hashTaxasEsperado() != null && !request.hashTaxasEsperado().equalsIgnoreCase(versoes.hashTaxasContratuais())) {
            alteradas.add("TAXAS_CONTRATUAIS");
        }
        if (request.hashRegrasEsperado() != null && !request.hashRegrasEsperado().equalsIgnoreCase(versoes.hashRegras())) {
            alteradas.add("REGRAS");
        }
        if (request.hashPendenciasEsperado() != null && !request.hashPendenciasEsperado().equalsIgnoreCase(versoes.hashPendenciasAceitas())) {
            alteradas.add("PENDENCIAS_ACEITAS");
        }
        if (request.hashRecorteEsperado() != null && !request.hashRecorteEsperado().equalsIgnoreCase(versoes.hashRecorte())) {
            if (alteradas.isEmpty()) {
                alteradas.add("METADADOS_OU_PERIODO");
            }
        }

        boolean consistente = alteradas.isEmpty();
        String mensagem = consistente
                ? "O recorte de população permanece consistente e inalterado no banco de dados."
                : "Divergência detectada nas seguintes fontes do recorte: " + String.join(", ", alteradas);

        return new ConsistenciaRecorteIaResponse(
                consistente,
                versoes.hashRecorte(),
                versoes.hashVendas(),
                versoes.hashRh(),
                versoes.hashTaxasContratuais(),
                versoes.hashRegras(),
                versoes.hashPendenciasAceitas(),
                alteradas,
                mensagem
        );
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE RESOLUÇÃO, HASH E DTO
    // =========================================================================

    private record PeriodoCalculado(
            String competenciaReferencia,
            List<String> competencias,
            LocalDate dataInicioCampanha,
            LocalDate dataFimCampanha,
            LocalDate dataInicioRecorte,
            LocalDate dataFimRecorte,
            long diasInclusivos,
            BigDecimal fatorProjecao
    ) {}

    private PeriodoCalculado calcularPeriodo(RecortePopulacaoIaRequest request, Campanha campanha, CampanhaVersao versao) {
        LocalDate iniCamp = request.dataInicio();
        LocalDate fimCamp = request.dataFim();

        if (iniCamp == null && versao != null) {
            iniCamp = versao.getDataInicio();
        } else if (iniCamp == null && campanha != null) {
            iniCamp = campanha.inicioParaRevisao();
        }

        if (fimCamp == null && versao != null) {
            fimCamp = versao.getDataFim();
        } else if (fimCamp == null && campanha != null) {
            fimCamp = campanha.fimParaRevisao();
        }

        LocalDate iniRecorte;
        LocalDate fimRecorte;
        String compRef = null;
        List<String> comps = new ArrayList<>();

        if (request.competencia() != null && !request.competencia().isBlank()) {
            YearMonth ym = YearMonth.parse(request.competencia().trim());
            compRef = ym.toString();
            iniRecorte = ym.atDay(1);
            fimRecorte = ym.atEndOfMonth();
            comps.add(compRef);

            if (iniCamp == null) iniCamp = iniRecorte;
            if (fimCamp == null) fimCamp = fimRecorte;
        } else {
            if (iniCamp == null || fimCamp == null) {
                throw new BusinessException("A competência ou as datas de início e fim da campanha devem ser informadas para o recorte.");
            }
            iniRecorte = iniCamp;
            fimRecorte = fimCamp;

            YearMonth atual = YearMonth.from(iniRecorte);
            YearMonth limite = YearMonth.from(fimRecorte);
            while (!atual.isAfter(limite)) {
                comps.add(atual.toString());
                atual = atual.plusMonths(1);
            }
            compRef = comps.size() == 1 ? comps.get(0) : null;
        }

        if (fimCamp.isBefore(iniCamp)) {
            throw new BusinessException("A data final da campanha não pode ser anterior à data inicial.");
        }

        long dias = ChronoUnit.DAYS.between(iniCamp, fimCamp) + 1;
        BigDecimal fator = BigDecimal.valueOf(dias).divide(DIAS_BASE_MES, 6, RoundingMode.HALF_UP);

        return new PeriodoCalculado(compRef, comps, iniCamp, fimCamp, iniRecorte, fimRecorte, dias, fator);
    }

    private List<RegraItemRequest> resolverRegras(RecortePopulacaoIaRequest request, Campanha campanha, CampanhaVersao versao) {
        if (request.regras() != null && !request.regras().isEmpty()) {
            return request.regras();
        }

        if (campanha != null) {
            List<Regra> regras = Collections.emptyList();
            if (versao != null) {
                regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
            }
            if (regras.isEmpty()) {
                regras = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId());
            }
            if (regras.isEmpty() && campanha.getRegras() != null) {
                regras = campanha.getRegras().stream().filter(r -> r.getRemovidoEm() == null).toList();
            }
            return regras.stream().map(referenciaPublicoService::toItemRequest).toList();
        }

        return Collections.emptyList();
    }

    private List<RegraConcorrenteRecorteIaDTO> buscarRegrasConcorrentesAtivas(
            LocalDate ini, LocalDate fim, Long ignorarCampanhaId, List<RegraItemRequest> regrasAvaliadas
    ) {
        List<RegraConcorrenteRecorteIaDTO> concorrentes = new ArrayList<>();
        List<Regra> ativas = regraRepository.findRegrasAtivasNoPeriodo(ini, fim, StatusRegra.ATIVA);

        for (Regra r : ativas) {
            if (r.getId() == null || r.getId().equals(1L)) {
                continue; // Ignora regra base técnica
            }
            Long campId = r.getCampanha() != null ? r.getCampanha().getId() : null;
            if (ignorarCampanhaId != null && ignorarCampanhaId.equals(campId)) {
                continue; // Ignora regras da própria campanha
            }

            int esp = TaxaComissaoResolver.especificidade(
                    r.getCodMarca(), r.getCodLoja(), r.getCodCargo(), r.getMatricula(), r.getCanal()
            );

            concorrentes.add(new RegraConcorrenteRecorteIaDTO(
                    r.getId(),
                    campId,
                    r.getCampanha() != null ? r.getCampanha().getTitulo() : null,
                    r.getNome(),
                    r.getCanal(),
                    r.getCodMarca(),
                    r.getCodLoja(),
                    r.getCodCargo(),
                    r.getMatricula(),
                    r.getTaxa(),
                    esp,
                    r.getDataInicio(),
                    r.getDataFim(),
                    r.getValorMinimo(),
                    r.getValorMaximo()
            ));
        }

        concorrentes.sort(Comparator.comparing(RegraConcorrenteRecorteIaDTO::regraId));
        return concorrentes;
    }

    private record RegistroVersao(int versao, String idLoteOrigem, String idLoteAlteracao) {}

    private ResumoVersaoRegistrosDTO calcularResumoVersoes(List<RegistroVersao> registros) {
        if (registros.isEmpty()) {
            return new ResumoVersaoRegistrosDTO(0, 1, 1, 0, List.of(), List.of());
        }
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;
        int editados = 0;
        Set<String> lotesOrig = new TreeSet<>();
        Set<String> lotesAlt = new TreeSet<>();

        for (RegistroVersao rv : registros) {
            int v = rv.versao();
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;
            if (v > 1) editados++;
            if (rv.idLoteOrigem() != null) lotesOrig.add(rv.idLoteOrigem());
            if (rv.idLoteAlteracao() != null) lotesAlt.add(rv.idLoteAlteracao());
        }

        return new ResumoVersaoRegistrosDTO(
                registros.size(),
                minV == Integer.MAX_VALUE ? 1 : minV,
                maxV == Integer.MIN_VALUE ? 1 : maxV,
                editados,
                new ArrayList<>(lotesOrig),
                new ArrayList<>(lotesAlt)
        );
    }

    private PropostaRegraDTO converterParaPropostaDTO(RegraItemRequest r, LocalDate iniCamp, LocalDate fimCamp) {
        FiltrosRegraDTO filtros = new FiltrosRegraDTO(
                r.canal(), r.codMarca(), r.descrMarca(), r.codLoja(), r.codCargo(), r.descriCargo(), r.matricula()
        );
        FaixaValorDTO faixa = new FaixaValorDTO(
                r.valorMinimo(), r.minInclusivo(), r.valorMaximo(), r.maxInclusivo()
        );
        OperacaoBaseDTO operacao = (r.tipoOperacao() != null && r.tipoOperacao() != TipoOperacaoBase.DEFINIR_TAXA)
                ? new OperacaoBaseDTO(r.tipoOperacao(), r.valorAjuste(), new ReferenciaBaseDTO("BASE_COMISS", r.taxaBaseConsultada(), "Taxa Base"))
                : OperacaoBaseDTO.definirTaxa(r.taxa());

        PeriodoVigenciaDTO vig = PeriodoVigenciaDTO.de(
                iniCamp,
                fimCamp
        );

        return new PropostaRegraDTO(
                r.blocoId(), r.nome(), filtros, faixa, operacao, r.taxa(),
                List.of(), Map.of(), List.of(), true, null, null, vig,
                r.bonusPercentual(), r.bonusValorFixo()
        );
    }

    private Long extrairIdRegra(String identificadorLogico) {
        if (identificadorLogico == null) return null;
        if (identificadorLogico.startsWith("regra-")) {
            try {
                return Long.parseLong(identificadorLogico.substring("regra-".length()));
            } catch (NumberFormatException ignored) {}
        }
        try {
            return Long.parseLong(identificadorLogico);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    // =========================================================================
    // HASHING DETERMINÍSTICO CANÔNICO SHA-256
    // =========================================================================

    private String calcularHashVendas(List<VendaRecorteIaDTO> vendas) {
        List<String> linhas = vendas.stream().map(v -> row(
                v.id(), v.matricula(), v.codLoja(), v.codMarca(), v.codCargo(),
                v.valorVenda(), v.dataVenda(), v.canal(), v.tipoVenda(),
                v.versao(), v.idLoteOrigem(), v.idLoteUltimaAlteracao()
        )).toList();
        return sha256("vendas:v1", linhas);
    }

    private String calcularHashRh(List<ColaboradorRhRecorteIaDTO> dadosRh) {
        List<String> linhas = dadosRh.stream().map(r -> row(
                r.id(), r.matricula(), r.codLoja(), r.codCargo(),
                r.dataAdmissao(), r.dataDemissao(),
                r.versao(), r.idLoteOrigem(), r.idLoteUltimaAlteracao()
        )).toList();
        return sha256("rh:v1", linhas);
    }

    private String calcularHashTaxas(List<TaxaContratualRecorteIaDTO> taxas) {
        List<String> linhas = taxas.stream().map(t -> row(
                t.codMarca(), t.codCargo(), t.competencia(), t.taxa(), t.idLoteOrigem()
        )).toList();
        return sha256("taxas:v1", linhas);
    }

    private String calcularHashRegras(List<RegraRecorteIaDTO> regras) {
        List<String> linhas = regras.stream().map(r -> row(
                r.regraId(), r.blocoId(), r.nomeRegra(), r.canal(), r.codMarca(), r.codLoja(), r.codCargo(), r.matricula(),
                r.valorMinimo(), r.minInclusivo(), r.valorMaximo(), r.maxInclusivo(),
                r.tipoOperacao(), r.valorAjuste(), r.taxaBaseConsultada(), r.taxa(),
                r.bonusPercentual(), r.bonusValorFixo(), r.especificidade(), r.dataInicio(), r.dataFim()
        )).toList();
        return sha256("regras:v1", linhas);
    }

    private String calcularHashPendencias(List<PendenciaAceitaRecorteIaDTO> pendencias) {
        List<String> linhas = pendencias.stream().map(p -> row(
                p.inconsistenciaId(), p.reportId(), p.idLote(), p.tipoBase(), p.competencia(),
                p.linha(), p.campo(), p.severidade(), p.aceitaPor(), p.motivoAceite()
        )).toList();
        return sha256("pendencias:v1", linhas);
    }

    private static String row(Object... values) {
        try {
            List<String> canonical = new ArrayList<>();
            for (Object v : values) {
                if (v == null) {
                    canonical.add(null);
                } else if (v instanceof BigDecimal num) {
                    canonical.add(num.stripTrailingZeros().toPlainString());
                } else {
                    canonical.add(v.toString());
                }
            }
            return JSON.writeValueAsString(canonical);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar linha canônica para hash.", e);
        }
    }

    private static String sha256(String namespace, List<String> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(namespace.getBytes(StandardCharsets.UTF_8));
            List<String> sorted = new ArrayList<>(rows);
            sorted.sort(String::compareTo);
            for (String row : sorted) {
                byte[] bytes = row.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao gerar SHA-256.", e);
        }
    }
}
