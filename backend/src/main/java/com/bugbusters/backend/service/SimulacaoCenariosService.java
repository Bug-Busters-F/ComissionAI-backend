package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.referencia.RecortePublicoRequest;
import com.bugbusters.backend.dto.simulacao.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.Simulacao;
import com.bugbusters.backend.model.SimulacaoVenda;
import com.bugbusters.backend.model.StatusAceiteSimulacao;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.SimulacaoRepository;
import com.bugbusters.backend.repository.SimulacaoVendaRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.criteria.Predicate;

/**
 * Serviço responsável pela simulação em sandbox e persistência isolada (BACK-6 / BACK-7).
 *
 * Características e regras de negócio:
 * 1. Simulação isolada: NÃO grava em tb_resultado_calculo, NÃO gera logs em tb_log_calculo_imutavel
 *    e NÃO altera datas ou valores em tb_sales. Persiste em estruturas dedicadas (tb_simulacao e tb_simulacao_venda).
 * 2. Recorte de vendas: opera sobre a união de públicos da proposta na competência histórica informada.
 * 3. Cenário Base (Documento): calcula a comissão pela taxa padrão de tb_basecomiss para Marca + Cargo,
 *    ignorando regras promocionais de outras campanhas ativas em produção.
 * 4. Cenário Proposta (100%): aplica hipoteticamente as regras propostas sem alterar datas históricas.
 *    Se nenhuma regra se aplicar a uma venda, recorre ao fallback da taxa base contratual do Cenário 1.
 * 5. Cenários de Sensibilidade (80%, 120%, 150%): recalcula por venda escalando o valor bruto,
 *    reavaliando dinamicamente as faixas monetárias (inclusive transição/mudança de faixa).
 * 6. Projeção temporal: pro-rata calculada por (dias inclusivos da campanha ÷ 30).
 * 7. Precisão decimal e arredondamento idênticos ao motor de produção (HALF_UP, escala 2 para moeda e 4 para taxa).
 * 8. Consultas paginadas, totais independentes da página e memória explicativa de cálculo sem chamadas à IA.
 */
@Service
public class SimulacaoCenariosService {

    private static final Logger log = LoggerFactory.getLogger(SimulacaoCenariosService.class);

    private static final BigDecimal FATOR_80 = new BigDecimal("0.80");
    private static final BigDecimal FATOR_100 = new BigDecimal("1.00");
    private static final BigDecimal FATOR_120 = new BigDecimal("1.20");
    private static final BigDecimal FATOR_150 = new BigDecimal("1.50");
    private static final BigDecimal DIAS_BASE_MES = new BigDecimal("30");
    private static final Locale LOCALE_PT_BR = Locale.forLanguageTag("pt-BR");

    private final SaleRepository saleRepository;
    private final BaseComissRepository baseComissRepository;
    private final CampanhaRepository campanhaRepository;
    private final ReferenciaPublicoService referenciaPublicoService;
    private final SimulacaoRepository simulacaoRepository;
    private final SimulacaoVendaRepository simulacaoVendaRepository;
    private final CampanhaSimulacaoValidator campanhaSimulacaoValidator;
    private final CampanhaService campanhaService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SimulacaoCenariosService(
            SaleRepository saleRepository,
            BaseComissRepository baseComissRepository,
            CampanhaRepository campanhaRepository,
            ReferenciaPublicoService referenciaPublicoService,
            SimulacaoRepository simulacaoRepository,
            SimulacaoVendaRepository simulacaoVendaRepository,
            CampanhaSimulacaoValidator campanhaSimulacaoValidator,
            CampanhaService campanhaService) {
        this.saleRepository = saleRepository;
        this.baseComissRepository = baseComissRepository;
        this.campanhaRepository = campanhaRepository;
        this.referenciaPublicoService = referenciaPublicoService;
        this.simulacaoRepository = simulacaoRepository;
        this.simulacaoVendaRepository = simulacaoVendaRepository;
        this.campanhaSimulacaoValidator = campanhaSimulacaoValidator != null 
                ? campanhaSimulacaoValidator 
                : new CampanhaSimulacaoValidator(simulacaoRepository, null);
        this.campanhaService = campanhaService;
        this.objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    public SimulacaoCenariosService(
            SaleRepository saleRepository,
            BaseComissRepository baseComissRepository,
            CampanhaRepository campanhaRepository,
            ReferenciaPublicoService referenciaPublicoService,
            SimulacaoRepository simulacaoRepository,
            SimulacaoVendaRepository simulacaoVendaRepository) {
        this(saleRepository, baseComissRepository, campanhaRepository, referenciaPublicoService, simulacaoRepository, simulacaoVendaRepository, null, null);
    }

    public SimulacaoCenariosService(
            SaleRepository saleRepository,
            BaseComissRepository baseComissRepository,
            CampanhaRepository campanhaRepository,
            ReferenciaPublicoService referenciaPublicoService) {
        this(saleRepository, baseComissRepository, campanhaRepository, referenciaPublicoService, null, null, null, null);
    }

    public record ResultadoSimulacaoInterna(
            SimulacaoCenariosResponse response,
            Campanha campanha,
            List<VendaSimuladaDetalheDTO> todasVendas,
            List<RegraItemRequest> regrasAvaliadas
    ) {}

    /**
     * Executa a simulação completa em memória (sandbox puro).
     */
    @Transactional(readOnly = true)
    public SimulacaoCenariosResponse simularCenarios(SimulacaoCenariosRequest request) {
        return executarSimulacao(request).response();
    }

    /**
     * Executa a simulação completa e persiste os resultados e memória do cálculo
     * nas tabelas dedicadas tb_simulacao e tb_simulacao_venda (BACK-7).
     */
    @Transactional
    public SimulacaoCenariosResponse simularEPersistir(SimulacaoCenariosRequest request) {
        ResultadoSimulacaoInterna resultado = executarSimulacao(request);
        SimulacaoCenariosResponse resp = resultado.response();

        if (simulacaoRepository == null || simulacaoVendaRepository == null) {
            log.warn("Repositórios de simulação não injetados. Retornando resposta em memória.");
            return resp;
        }

        Simulacao simulacao = new Simulacao();
        UUID protocolo = UUID.randomUUID();
        simulacao.setProtocolo(protocolo);
        simulacao.setExecutadoEm(OffsetDateTime.now());
        simulacao.setCompetencia(resp.competencia());
        simulacao.setCampanha(resultado.campanha());
        if (resultado.campanha() != null) {
            simulacao.setCampanhaVersao(resultado.campanha().getVersaoProposta() != null
                    ? resultado.campanha().getVersaoProposta() : resultado.campanha().getVersaoVigente());
        }
        simulacao.setTituloCampanha(resp.tituloCampanha());
        simulacao.setDataInicioCampanha(resp.dataInicioCampanha());
        simulacao.setDataFimCampanha(resp.dataFimCampanha());
        simulacao.setDiasInclusivos(resp.diasInclusivos());
        simulacao.setFatorProjecao(resp.fatorProjecao());
        simulacao.setOrigemDados("HISTORICO_VENDAS_COMPETENCIA");

        SimulacaoCenariosRequest snapshotRequest = new SimulacaoCenariosRequest(
                resp.competencia(),
                request.campanhaId(),
                resp.dataInicioCampanha(),
                resp.dataFimCampanha(),
                resultado.regrasAvaliadas(),
                request.limite(),
                request.pagina(),
                request.apenasComAlteracao(),
                request.apenasComMudancaFaixa()
        );

        try {
            simulacao.setPremissasSnapshot(objectMapper != null ? objectMapper.writeValueAsString(snapshotRequest) : "{}");
        } catch (Exception e) {
            simulacao.setPremissasSnapshot("{}");
        }

        if (campanhaSimulacaoValidator != null) {
            String hash = campanhaSimulacaoValidator.calcularHashPropostaSimulacao(
                    resp.dataInicioCampanha(),
                    resp.dataFimCampanha(),
                    resultado.regrasAvaliadas(),
                    resp.competencia()
            );
            simulacao.setHashProposta(hash);
        }

        simulacao.setMemoriaCalculoConsolidada(resp.memoriaCalculoConsolidada());
        simulacao.setTotalVendasRecortadas(resp.totalVendasRecortadas());
        simulacao.setValorTotalRecortado(resp.valorTotalRecortado());

        // Consolidado 100%
        CenarioConsolidadoDTO c100 = resp.cenario100();
        simulacao.setTotalComissaoBase(c100.totalComissaoBase());
        simulacao.setTotalComissaoProposta(c100.totalComissaoProposta());
        simulacao.setDeltaComissao(c100.deltaComissao());
        simulacao.setDeltaPercentual(c100.deltaPercentual());
        simulacao.setTaxaMediaEfetivaBase(c100.taxaMediaEfetivaBase());
        simulacao.setTaxaMediaEfetivaProposta(c100.taxaMediaEfetivaProposta());
        simulacao.setComissaoProjetada(c100.comissaoProjetada());
        simulacao.setDeltaProjetado(c100.deltaProjetado());
        simulacao.setQtdVendasComMudancaFaixa(c100.qtdVendasComMudancaFaixa());
        simulacao.setQtdVendasComRegraAplicada(c100.qtdVendasComRegraAplicada());
        simulacao.setQtdVendasComFallbackBase(c100.qtdVendasComFallbackBase());
        long impCount = resultado.todasVendas().stream().filter(v -> !v.valida()).count();
        simulacao.setQtdVendasComImpedimento((int) impCount);

        // Consolidado 80%
        CenarioConsolidadoDTO c80 = resp.cenario80();
        simulacao.setVendas80(c80.totalVendas());
        simulacao.setComissaoBase80(c80.totalComissaoBase());
        simulacao.setComissaoProposta80(c80.totalComissaoProposta());
        simulacao.setDelta80(c80.deltaComissao());
        simulacao.setDeltaPerc80(c80.deltaPercentual());
        simulacao.setTaxaMedia80(c80.taxaMediaEfetivaProposta());
        simulacao.setProjetada80(c80.comissaoProjetada());
        simulacao.setDeltaProj80(c80.deltaProjetado());
        simulacao.setMudancaFaixa80(c80.qtdVendasComMudancaFaixa());

        // Consolidado 120%
        CenarioConsolidadoDTO c120 = resp.cenario120();
        simulacao.setVendas120(c120.totalVendas());
        simulacao.setComissaoBase120(c120.totalComissaoBase());
        simulacao.setComissaoProposta120(c120.totalComissaoProposta());
        simulacao.setDelta120(c120.deltaComissao());
        simulacao.setDeltaPerc120(c120.deltaPercentual());
        simulacao.setTaxaMedia120(c120.taxaMediaEfetivaProposta());
        simulacao.setProjetada120(c120.comissaoProjetada());
        simulacao.setDeltaProj120(c120.deltaProjetado());
        simulacao.setMudancaFaixa120(c120.qtdVendasComMudancaFaixa());

        // Consolidado 150%
        CenarioConsolidadoDTO c150 = resp.cenario150();
        simulacao.setVendas150(c150.totalVendas());
        simulacao.setComissaoBase150(c150.totalComissaoBase());
        simulacao.setComissaoProposta150(c150.totalComissaoProposta());
        simulacao.setDelta150(c150.deltaComissao());
        simulacao.setDeltaPerc150(c150.deltaPercentual());
        simulacao.setTaxaMedia150(c150.taxaMediaEfetivaProposta());
        simulacao.setProjetada150(c150.comissaoProjetada());
        simulacao.setDeltaProj150(c150.deltaProjetado());
        simulacao.setMudancaFaixa150(c150.qtdVendasComMudancaFaixa());

        simulacao = simulacaoRepository.save(simulacao);

        // Grava todas as vendas simuladas do recorte em lote
        List<SimulacaoVenda> itens = new ArrayList<>();
        for (VendaSimuladaDetalheDTO v : resultado.todasVendas()) {
            SimulacaoVenda item = new SimulacaoVenda();
            item.setSimulacao(simulacao);
            item.setIdVenda(v.id());
            item.setMatricula(v.matricula());
            item.setCodCargo(v.codCargo());
            item.setDescriCargo(v.descriCargo());
            item.setCodLoja(v.codLoja());
            item.setCodMarca(v.codMarca());
            item.setDescrMarca(v.descrMarca());
            item.setCanal(v.canal());
            item.setDataVenda(v.dataVenda());
            item.setValorVendaOriginal(v.valorVendaOriginal());
            item.setTaxaBaseContratual(v.taxaBaseContratual());
            item.setValorComissaoBase(v.valorComissaoBase());

            // 100%
            VendaSimuladaCenarioItemDTO i100 = v.cenario100();
            item.setRegraId100(i100.regraId());
            item.setRegraNome100(i100.regraNome());
            item.setOrigemTaxa100(i100.origemTaxa());
            item.setAtendeFaixa100(i100.atendeFaixa());
            item.setTaxaAplicada100(i100.taxaAplicada());
            item.setValorBonusPerc100(i100.valorBonusPerc());
            item.setValorBonusFixo100(i100.valorBonusFixo());
            item.setValorComissao100(i100.valorComissao());
            item.setDeltaComissao100(v.deltaComissao100());

            // 80%
            VendaSimuladaCenarioItemDTO i80 = v.cenario80();
            item.setValor80(i80.valorVenda());
            item.setRegraId80(i80.regraId());
            item.setRegraNome80(i80.regraNome());
            item.setOrigem80(i80.origemTaxa());
            item.setTaxa80(i80.taxaAplicada());
            item.setBonusPerc80(i80.valorBonusPerc());
            item.setBonusFixo80(i80.valorBonusFixo());
            item.setComissao80(i80.valorComissao());
            item.setMudouFaixa80(i80.mudouFaixaEmRelacaoA100());

            // 120%
            VendaSimuladaCenarioItemDTO i120 = v.cenario120();
            item.setValor120(i120.valorVenda());
            item.setRegraId120(i120.regraId());
            item.setRegraNome120(i120.regraNome());
            item.setOrigem120(i120.origemTaxa());
            item.setTaxa120(i120.taxaAplicada());
            item.setBonusPerc120(i120.valorBonusPerc());
            item.setBonusFixo120(i120.valorBonusFixo());
            item.setComissao120(i120.valorComissao());
            item.setMudouFaixa120(i120.mudouFaixaEmRelacaoA100());

            // 150%
            VendaSimuladaCenarioItemDTO i150 = v.cenario150();
            item.setValor150(i150.valorVenda());
            item.setRegraId150(i150.regraId());
            item.setRegraNome150(i150.regraNome());
            item.setOrigem150(i150.origemTaxa());
            item.setTaxa150(i150.taxaAplicada());
            item.setBonusPerc150(i150.valorBonusPerc());
            item.setBonusFixo150(i150.valorBonusFixo());
            item.setComissao150(i150.valorComissao());
            item.setMudouFaixa150(i150.mudouFaixaEmRelacaoA100());

            item.setValida(v.valida());
            item.setImpedimentos(v.impedimentos() != null ? String.join(";\n", v.impedimentos()) : null);
            item.setMemoriaCalculoItem(v.memoriaCalculo());

            itens.add(item);
        }

        if (!itens.isEmpty()) {
            simulacaoVendaRepository.saveAll(itens);
        }

        return new SimulacaoCenariosResponse(
                simulacao.getId(),
                simulacao.getProtocolo(),
                resp.competencia(),
                resp.totalVendasRecortadas(),
                resp.valorTotalRecortado(),
                resp.campanhaId(),
                resp.tituloCampanha(),
                resp.dataInicioCampanha(),
                resp.dataFimCampanha(),
                resp.diasInclusivos(),
                resp.fatorProjecao(),
                resp.cenario100(),
                resp.cenario80(),
                resp.cenario120(),
                resp.cenario150(),
                resp.totalVendasPaginadas(),
                resp.vendas(),
                resp.avisosGerais(),
                resp.memoriaCalculoConsolidada()
        );
    }

    /**
     * Registra o aceite formal de uma simulação persistida sem impedimentos (BACK-8).
     */
    @Transactional
    public AceiteSimulacaoResponse aceitarSimulacao(UUID simulacaoId, AceiteSimulacaoRequest request) {
        if (simulacaoRepository == null) {
            throw new BusinessException("Repositório de simulação indisponível.");
        }
        Simulacao simulacao = simulacaoRepository.findById(simulacaoId)
                .orElseThrow(() -> new ResourceNotFoundException("Simulação não encontrada com ID: " + simulacaoId));

        if (simulacao.getQtdVendasComImpedimento() > 0) {
            throw new BusinessException(String.format(
                    "Não é permitido aceitar uma simulação que contém %d venda(s) com impedimentos cadastrais ou ausência de taxas base.",
                    simulacao.getQtdVendasComImpedimento()));
        }

        Campanha campanha = simulacao.getCampanha();
        if (campanha == null && request != null && request.campanhaId() != null && campanhaRepository != null) {
            campanha = campanhaRepository.findById(request.campanhaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campanha não encontrada com ID: " + request.campanhaId()));
            simulacao.setCampanha(campanha);
        }

        OffsetDateTime agora = OffsetDateTime.now();

        if (campanha != null) {
            // Invalidar aceites anteriores da mesma campanha
            List<Simulacao> aceitesAnteriores = simulacaoRepository.findAllByCampanhaIdAndStatusAceite(
                    campanha.getId(), StatusAceiteSimulacao.ACEITA);
            for (Simulacao anterior : aceitesAnteriores) {
                // Uma revisão publicada é histórico auditável. Só substitui aceite da mesma proposta.
                if (!anterior.getId().equals(simulacao.getId())
                        && Objects.equals(anterior.getCampanhaVersao() != null ? anterior.getCampanhaVersao().getId() : null,
                                          simulacao.getCampanhaVersao() != null ? simulacao.getCampanhaVersao().getId() : null)) {
                    anterior.setStatusAceite(StatusAceiteSimulacao.INVALIDADA);
                    anterior.setInvalidadoEm(agora);
                    anterior.setMotivoInvalidacao("Substituída por novo aceite da simulação (Protocolo: " + simulacao.getProtocolo() + ")");
                }
            }
            if (!aceitesAnteriores.isEmpty()) {
                simulacaoRepository.saveAll(aceitesAnteriores);
            }
        }

        simulacao.setStatusAceite(StatusAceiteSimulacao.ACEITA);
        simulacao.setAceitoEm(agora);
        simulacao.setAceitoPor(request != null && request.aceitoPor() != null && !request.aceitoPor().isBlank() ? request.aceitoPor() : "gestor");
        simulacao.setMotivoAceite(request != null ? request.motivo() : null);
        simulacao.setInvalidadoEm(null);
        simulacao.setMotivoInvalidacao(null);

        Simulacao salva = simulacaoRepository.save(simulacao);

        if (campanha != null) {
            campanha.setSimulacaoAceita(salva);
            if (campanhaService != null) {
                campanhaService.publicarVersaoAceita(campanha, salva, request != null ? request.versaoEsperada() : null);
            } else {
                campanhaRepository.save(campanha);
            }
        }

        return new AceiteSimulacaoResponse(
                salva.getId(),
                salva.getProtocolo(),
                campanha != null ? campanha.getId() : null,
                campanha != null ? campanha.getTitulo() : "Proposta em Elaboração",
                salva.getStatusAceite(),
                salva.getAceitoEm(),
                salva.getAceitoPor(),
                salva.getMotivoAceite(),
                salva.getHashProposta(),
                "Aceite formal da simulação registrado com sucesso. A proposta está homologada para ativação."
        );
    }

    /**
     * Invalida o aceite de simulações vigentes de uma campanha quando houver mutação em regras ou vigência (BACK-8).
     */
    @Transactional
    public void invalidarAceiteCampanha(Campanha campanha, String motivo) {
        if (campanha == null || campanha.getId() == null) {
            return;
        }

        OffsetDateTime agora = OffsetDateTime.now();
        if (campanha.getSimulacaoAceita() != null) {
            Simulacao sim = campanha.getSimulacaoAceita();
            sim.setStatusAceite(StatusAceiteSimulacao.INVALIDADA);
            sim.setInvalidadoEm(agora);
            sim.setMotivoInvalidacao(motivo);
            if (simulacaoRepository != null) {
                simulacaoRepository.save(sim);
            }
            campanha.setSimulacaoAceita(null);
            campanhaRepository.save(campanha);
        }

        if (simulacaoRepository != null) {
            List<Simulacao> ativas = simulacaoRepository.findAllByCampanhaIdAndStatusAceite(
                    campanha.getId(), StatusAceiteSimulacao.ACEITA);
            for (Simulacao s : ativas) {
                s.setStatusAceite(StatusAceiteSimulacao.INVALIDADA);
                s.setInvalidadoEm(agora);
                s.setMotivoInvalidacao(motivo);
            }
            if (!ativas.isEmpty()) {
                simulacaoRepository.saveAll(ativas);
            }
        }
    }

    /**
     * Consulta lista paginada do histórico de simulações com filtros opcionais.
     */
    @Transactional(readOnly = true)
    public Page<SimulacaoResumoDTO> listarSimulacoes(String competencia, Long campanhaId, Pageable pageable) {
        Page<Simulacao> pagina;
        String compNormalizada = (competencia != null && !competencia.isBlank()) ? normalizarCompetencia(competencia) : null;

        if (compNormalizada != null && campanhaId != null) {
            pagina = simulacaoRepository.findByCompetenciaAndCampanhaIdOrderByExecutadoEmDesc(compNormalizada, campanhaId, pageable);
        } else if (compNormalizada != null) {
            pagina = simulacaoRepository.findByCompetenciaOrderByExecutadoEmDesc(compNormalizada, pageable);
        } else if (campanhaId != null) {
            pagina = simulacaoRepository.findByCampanhaIdOrderByExecutadoEmDesc(campanhaId, pageable);
        } else {
            pagina = simulacaoRepository.findAllByOrderByExecutadoEmDesc(pageable);
        }

        return pagina.map(this::converterParaResumoDTO);
    }

    /**
     * Consulta detalhe completo de uma simulação persistida por ID.
     */
    @Transactional(readOnly = true)
    public SimulacaoCenariosResponse buscarSimulacaoPorId(UUID id) {
        Simulacao s = simulacaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Simulação não encontrada com ID: " + id));

        Pageable p = PageRequest.of(0, 50);
        Page<SimulacaoVenda> vendasPage = simulacaoVendaRepository.findBySimulacaoIdOrderByDataVendaAsc(id, p);
        List<VendaSimuladaDetalheDTO> vendasDTO = vendasPage.getContent().stream()
                .map(this::converterParaVendaDetalheDTO)
                .toList();

        List<String> avisos = new ArrayList<>();
        if (s.getQtdVendasComImpedimento() > 0) {
            avisos.add(String.format("Foram identificadas %d vendas com impedimentos cadastrais nesta simulação.", s.getQtdVendasComImpedimento()));
        }
        avisos.add("Simulação recuperada da persistência isolada (tb_simulacao).");

        return new SimulacaoCenariosResponse(
                s.getId(),
                s.getProtocolo(),
                s.getCompetencia(),
                s.getTotalVendasRecortadas(),
                s.getValorTotalRecortado(),
                s.getCampanha() != null ? s.getCampanha().getId() : null,
                s.getTituloCampanha(),
                s.getDataInicioCampanha(),
                s.getDataFimCampanha(),
                s.getDiasInclusivos(),
                s.getFatorProjecao(),
                extrairCenario100(s),
                extrairCenario80(s),
                extrairCenario120(s),
                extrairCenario150(s),
                (int) vendasPage.getTotalElements(),
                vendasDTO,
                avisos,
                s.getMemoriaCalculoConsolidada()
        );
    }

    /**
     * Consulta vendas detalhadas paginadas de uma simulação persistida,
     * garantindo que os totais consolidados retornados sejam independentes da página.
     */
    @Transactional(readOnly = true)
    public SimulacaoVendasPaginadasDTO listarVendasPaginadas(
            UUID simulacaoId,
            Boolean apenasComAlteracao,
            Boolean apenasComMudancaFaixa,
            Boolean apenasComImpedimento,
            Pageable pageable) {

        Simulacao simulacao = simulacaoRepository.findById(simulacaoId)
                .orElseThrow(() -> new ResourceNotFoundException("Simulação não encontrada com ID: " + simulacaoId));

        Specification<SimulacaoVenda> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("simulacao").get("id"), simulacaoId));

            if (Boolean.TRUE.equals(apenasComAlteracao)) {
                predicates.add(cb.notEqual(root.get("deltaComissao100"), BigDecimal.ZERO));
            }

            if (Boolean.TRUE.equals(apenasComMudancaFaixa)) {
                predicates.add(cb.or(
                        cb.isTrue(root.get("mudouFaixa80")),
                        cb.isTrue(root.get("mudouFaixa120")),
                        cb.isTrue(root.get("mudouFaixa150"))
                ));
            }

            if (Boolean.TRUE.equals(apenasComImpedimento)) {
                predicates.add(cb.isFalse(root.get("valida")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SimulacaoVenda> pagina = simulacaoVendaRepository.findAll(spec, pageable);
        List<VendaSimuladaDetalheDTO> vendasDTO = pagina.getContent().stream()
                .map(this::converterParaVendaDetalheDTO)
                .toList();

        return new SimulacaoVendasPaginadasDTO(
                simulacao.getId(),
                simulacao.getProtocolo(),
                simulacao.getCompetencia(),
                simulacao.getTituloCampanha(),
                simulacao.getDiasInclusivos(),
                simulacao.getFatorProjecao(),
                extrairCenario100(simulacao),
                extrairCenario80(simulacao),
                extrairCenario120(simulacao),
                extrairCenario150(simulacao),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                vendasDTO
        );
    }

    /**
     * Consulta detalhe e rastreabilidade explicativa de uma venda individual da simulação.
     */
    @Transactional(readOnly = true)
    public VendaSimuladaDetalheDTO buscarVendaSimuladaDetalhe(UUID simulacaoId, UUID idVenda) {
        SimulacaoVenda sv = simulacaoVendaRepository.findBySimulacaoIdAndIdVenda(simulacaoId, idVenda)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Venda com ID %s não encontrada na simulação %s", idVenda, simulacaoId)));
        return converterParaVendaDetalheDTO(sv);
    }

    // =========================================================================
    // MÉTODOS DE CÁLCULO INTERNO E SANDBOX
    // =========================================================================

    private ResultadoSimulacaoInterna executarSimulacao(SimulacaoCenariosRequest request) {
        if (request == null) {
            throw new BusinessException("A requisição de simulação de cenários não pode ser nula.");
        }

        String competenciaStr = normalizarCompetencia(request.competencia());
        YearMonth ym = YearMonth.parse(competenciaStr);
        LocalDate inicioMes = ym.atDay(1);
        LocalDate fimMes = ym.atEndOfMonth();

        // 1. Resolução das regras candidatas e metadados da campanha
        Campanha campanha = null;
        if (request.campanhaId() != null) {
            campanha = campanhaRepository.findById(request.campanhaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campanha não encontrada com ID: " + request.campanhaId()));
        }

        List<RegraItemRequest> regrasAvaliadas = resolverRegras(request, campanha);
        if (regrasAvaliadas.isEmpty()) {
            throw new BusinessException("Nenhuma regra fornecida ou encontrada para a realização da simulação.");
        }

        // 2. Determinação do período hipotético e dias inclusivos da campanha para projeção
        LocalDate dataInicioCampanha = request.dataInicio();
        LocalDate dataFimCampanha = request.dataFim();

        if (dataInicioCampanha == null && campanha != null) {
            dataInicioCampanha = campanha.getDataInicio();
        }
        if (dataFimCampanha == null && campanha != null) {
            dataFimCampanha = campanha.getDataFim();
        }

        // Fallback para vigência: se não informado, adota o mês completo da competência (30 dias)
        if (dataInicioCampanha == null) {
            dataInicioCampanha = inicioMes;
        }
        if (dataFimCampanha == null) {
            dataFimCampanha = fimMes;
        }

        if (dataFimCampanha.isBefore(dataInicioCampanha)) {
            throw new BusinessException("A data final da campanha não pode ser anterior à data inicial.");
        }

        long diasInclusivos = ChronoUnit.DAYS.between(dataInicioCampanha, dataFimCampanha) + 1;
        BigDecimal fatorProjecao = BigDecimal.valueOf(diasInclusivos)
                .divide(DIAS_BASE_MES, 6, RoundingMode.HALF_UP);

        // 3. Carregamento de vendas e bases contratuais (tb_basecomiss) pré-carregadas em memória
        List<Sale> vendasDoMes = saleRepository.findVendasPorPeriodoComRelacionamentos(inicioMes, fimMes);
        List<BaseComiss> basesComissao = baseComissRepository.findAllComRelacionamentos();

        List<String> avisosGerais = new ArrayList<>();
        if (vendasDoMes.isEmpty()) {
            avisosGerais.add(String.format("A competência '%s' não possui nenhuma transação de venda registrada.", competenciaStr));
            SimulacaoCenariosResponse vazia = construirRespostaVazia(competenciaStr, campanha, dataInicioCampanha, dataFimCampanha, diasInclusivos, fatorProjecao, avisosGerais);
            return new ResultadoSimulacaoInterna(vazia, campanha, List.of(), regrasAvaliadas);
        }

        // 4. Recorte de público: identifica vendas que pertencem ao público da proposta (OU entre regras)
        List<Sale> vendasRecortadas = new ArrayList<>();
        for (Sale sale : vendasDoMes) {
            boolean pertenceAoPublico = false;
            for (RegraItemRequest regra : regrasAvaliadas) {
                if (referenciaPublicoService.atendePublico(regra, sale)) {
                    pertenceAoPublico = true;
                    break;
                }
            }
            if (pertenceAoPublico) {
                vendasRecortadas.add(sale);
            }
        }

        if (vendasRecortadas.isEmpty()) {
            avisosGerais.add(String.format("Nenhuma venda da competência '%s' pertence ao público-alvo das regras avaliadas.", competenciaStr));
            SimulacaoCenariosResponse vazia = construirRespostaVazia(competenciaStr, campanha, dataInicioCampanha, dataFimCampanha, diasInclusivos, fatorProjecao, avisosGerais);
            return new ResultadoSimulacaoInterna(vazia, campanha, List.of(), regrasAvaliadas);
        }

        // 5. Processamento por venda dos cenários Base, 100%, 80%, 120% e 150%
        List<VendaSimuladaDetalheDTO> todasVendasSimuladas = new ArrayList<>();
        int countVendasComImpedimento = 0;

        for (Sale sale : vendasRecortadas) {
            VendaSimuladaDetalheDTO detalhe = processarVendaSimulada(sale, regrasAvaliadas, basesComissao, competenciaStr);
            todasVendasSimuladas.add(detalhe);
            if (!detalhe.valida()) {
                countVendasComImpedimento++;
            }
        }

        if (countVendasComImpedimento > 0) {
            avisosGerais.add(String.format("%d de %d vendas recortadas possuem inconsistências cadastrais ou falta de taxa base em tb_basecomiss.",
                    countVendasComImpedimento, todasVendasSimuladas.size()));
        }

        // 6. Consolidação dos indicadores por cenário
        CenarioConsolidadoDTO cenario100 = consolidarCenario(todasVendasSimuladas, FATOR_100, "Cenário 100% (Proposta Revisada)", fatorProjecao, VendaSimuladaDetalheDTO::cenario100);
        CenarioConsolidadoDTO cenario80 = consolidarCenario(todasVendasSimuladas, FATOR_80, "Cenário 80% (Volume Reduzido)", fatorProjecao, VendaSimuladaDetalheDTO::cenario80);
        CenarioConsolidadoDTO cenario120 = consolidarCenario(todasVendasSimuladas, FATOR_120, "Cenário 120% (Crescimento Otimista)", fatorProjecao, VendaSimuladaDetalheDTO::cenario120);
        CenarioConsolidadoDTO cenario150 = consolidarCenario(todasVendasSimuladas, FATOR_150, "Cenário 150% (Alta Performance)", fatorProjecao, VendaSimuladaDetalheDTO::cenario150);

        // 7. Geração da memória explicativa consolidada
        String memoriaConsolidada = gerarMemoriaCalculoConsolidada(
                competenciaStr,
                campanha,
                dataInicioCampanha,
                dataFimCampanha,
                diasInclusivos,
                fatorProjecao,
                todasVendasSimuladas.size(),
                cenario100.totalVendas(),
                cenario100,
                cenario80,
                cenario120,
                cenario150
        );

        // 8. Filtros e paginação da listagem de vendas
        List<VendaSimuladaDetalheDTO> vendasFiltradas = todasVendasSimuladas.stream()
                .filter(v -> !Boolean.TRUE.equals(request.apenasComAlteracao()) || v.deltaComissao100().signum() != 0)
                .filter(v -> !Boolean.TRUE.equals(request.apenasComMudancaFaixa()) || (
                        (v.cenario80() != null && v.cenario80().mudouFaixaEmRelacaoA100()) ||
                        (v.cenario120() != null && v.cenario120().mudouFaixaEmRelacaoA100()) ||
                        (v.cenario150() != null && v.cenario150().mudouFaixaEmRelacaoA100())
                ))
                .toList();

        int pagina = request.pagina() != null ? request.pagina() : 0;
        int limite = request.limite() != null ? request.limite() : 500;
        int fromIndex = Math.min(pagina * limite, vendasFiltradas.size());
        int toIndex = Math.min(fromIndex + limite, vendasFiltradas.size());
        List<VendaSimuladaDetalheDTO> vendasPaginadas = vendasFiltradas.subList(fromIndex, toIndex);

        SimulacaoCenariosResponse response = new SimulacaoCenariosResponse(
                null,
                null,
                competenciaStr,
                todasVendasSimuladas.size(),
                cenario100.totalVendas(),
                campanha != null ? campanha.getId() : null,
                campanha != null ? campanha.getTitulo() : "Simulação de Regras Avulsas",
                dataInicioCampanha,
                dataFimCampanha,
                diasInclusivos,
                fatorProjecao,
                cenario100,
                cenario80,
                cenario120,
                cenario150,
                vendasPaginadas.size(),
                vendasPaginadas,
                avisosGerais,
                memoriaConsolidada
        );

        return new ResultadoSimulacaoInterna(response, campanha, todasVendasSimuladas, regrasAvaliadas);
    }

    private VendaSimuladaDetalheDTO processarVendaSimulada(
            Sale sale,
            List<RegraItemRequest> regrasAvaliadas,
            List<BaseComiss> basesComissao,
            String competenciaStr) {

        List<String> impedimentos = new ArrayList<>();
        Registration reg = sale.getRegistration();
        Position pos = (reg != null) ? reg.getPosition() : null;
        Brand brand = sale.getBrand();
        BigDecimal valorOriginal = sale.getValue() != null ? sale.getValue() : BigDecimal.ZERO;

        // 1. Validações cadastrais essenciais
        if (reg == null || reg.getRegistration() == null || reg.getRegistration().isBlank()) {
            impedimentos.add("Colaborador não identificado ou sem vínculo com o RH");
        }
        if (pos == null) {
            impedimentos.add("Colaborador sem cargo cadastrado no RH");
        }
        if (sale.getStore() == null || sale.getStore().getCode() == null) {
            impedimentos.add("Venda sem loja vinculada");
        }
        if (brand == null || brand.getCode() == null) {
            impedimentos.add("Venda sem marca vinculada");
        }
        if (valorOriginal.compareTo(BigDecimal.ZERO) <= 0) {
            impedimentos.add("Valor da venda inválido: deve ser estritamente positivo");
        }
        if (reg != null && sale.getSaleDate() != null) {
            if (reg.getAdmissDate() != null && sale.getSaleDate().isBefore(reg.getAdmissDate())) {
                impedimentos.add(String.format("Data da venda (%s) é anterior à admissão (%s)", sale.getSaleDate(), reg.getAdmissDate()));
            }
            if (reg.getDemissDate() != null && sale.getSaleDate().isAfter(reg.getDemissDate())) {
                impedimentos.add(String.format("Data da venda (%s) é posterior à demissão (%s)", sale.getSaleDate(), reg.getDemissDate()));
            }
        }

        // 2. Resolução da taxa base contratual (tb_basecomiss) para a venda
        Optional<BigDecimal> baseRateOpt = Optional.empty();
        if (brand != null && pos != null) {
            baseRateOpt = buscarTaxaBase(brand, pos, sale.getSaleDate(), basesComissao);
            if (baseRateOpt.isEmpty()) {
                impedimentos.add(String.format("Taxa base contratual ausente em tb_basecomiss para Marca '%s' e Cargo '%s'",
                        brand.getDescription() != null ? brand.getDescription() : brand.getCode(),
                        pos.getDescription() != null ? pos.getDescription() : pos.getCode()));
            }
        }

        BigDecimal taxaBaseContratual = baseRateOpt.orElse(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal comissaoBase100 = valorOriginal.multiply(taxaBaseContratual).setScale(2, RoundingMode.HALF_UP);

        // 3. Regras da proposta que cobrem as dimensões do público desta venda
        List<RegraItemRequest> regrasQueCobremPublico = regrasAvaliadas.stream()
                .filter(r -> referenciaPublicoService.atendePublico(r, sale))
                .toList();

        // 4. Executa os cenários: 100%, 80%, 120% e 150%
        BigDecimal v100 = valorOriginal;
        BigDecimal v80 = valorOriginal.multiply(FATOR_80).setScale(2, RoundingMode.HALF_UP);
        BigDecimal v120 = valorOriginal.multiply(FATOR_120).setScale(2, RoundingMode.HALF_UP);
        BigDecimal v150 = valorOriginal.multiply(FATOR_150).setScale(2, RoundingMode.HALF_UP);

        VendaSimuladaCenarioItemDTO item100 = resolverCenarioVenda(v100, FATOR_100, regrasQueCobremPublico, taxaBaseContratual, null);
        VendaSimuladaCenarioItemDTO item80 = resolverCenarioVenda(v80, FATOR_80, regrasQueCobremPublico, taxaBaseContratual, item100);
        VendaSimuladaCenarioItemDTO item120 = resolverCenarioVenda(v120, FATOR_120, regrasQueCobremPublico, taxaBaseContratual, item100);
        VendaSimuladaCenarioItemDTO item150 = resolverCenarioVenda(v150, FATOR_150, regrasQueCobremPublico, taxaBaseContratual, item100);

        BigDecimal deltaComissao100 = item100.valorComissao().subtract(comissaoBase100).setScale(2, RoundingMode.HALF_UP);
        boolean valida = impedimentos.isEmpty();

        String memoriaCalculo = gerarMemoriaCalculoItem(
                sale, item100, item80, item120, item150,
                taxaBaseContratual, comissaoBase100, deltaComissao100, impedimentos
        );

        return new VendaSimuladaDetalheDTO(
                sale.getId(),
                reg != null ? reg.getRegistration() : null,
                pos != null ? pos.getCode() : null,
                pos != null ? pos.getDescription() : null,
                sale.getStore() != null ? sale.getStore().getCode() : null,
                brand != null ? brand.getCode() : null,
                brand != null ? brand.getDescription() : null,
                sale.getSaleChannel() != null ? sale.getSaleChannel() : "PADRAO",
                sale.getSaleDate(),
                valorOriginal,
                taxaBaseContratual,
                comissaoBase100,
                item100,
                item80,
                item120,
                item150,
                deltaComissao100,
                valida,
                impedimentos,
                memoriaCalculo
        );
    }

    private VendaSimuladaCenarioItemDTO resolverCenarioVenda(
            BigDecimal valorVendaEscalado,
            BigDecimal fatorEscala,
            List<RegraItemRequest> regrasQueCobremPublico,
            BigDecimal taxaBaseContratual,
            VendaSimuladaCenarioItemDTO cenario100Referencia) {

        List<RegraItemRequest> regrasElegiveisNaFaixa = regrasQueCobremPublico.stream()
                .filter(r -> referenciaPublicoService.atendeFaixaValor(r, valorVendaEscalado))
                .toList();

        if (regrasElegiveisNaFaixa.isEmpty()) {
            BigDecimal comissaoFallback = valorVendaEscalado.multiply(taxaBaseContratual).setScale(2, RoundingMode.HALF_UP);
            boolean mudouFaixa = false;
            if (cenario100Referencia != null) {
                mudouFaixa = cenario100Referencia.regraId() != null || !"BASE_COMISS".equals(cenario100Referencia.origemTaxa());
            }

            return new VendaSimuladaCenarioItemDTO(
                    fatorEscala,
                    valorVendaEscalado,
                    null,
                    "Fallback Taxa Base Contratual",
                    "BASE_COMISS",
                    false,
                    taxaBaseContratual,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    comissaoFallback,
                    mudouFaixa
            );
        }

        int maxEsp = regrasElegiveisNaFaixa.stream()
                .mapToInt(referenciaPublicoService::calcularEspecificidade)
                .max()
                .orElse(0);

        List<RegraItemRequest> maisEspecificas = regrasElegiveisNaFaixa.stream()
                .filter(r -> referenciaPublicoService.calcularEspecificidade(r) == maxEsp)
                .toList();

        if (maisEspecificas.size() > 1) {
            List<RegraItemRequest> comFaixa = maisEspecificas.stream()
                    .filter(r -> r.valorMinimo() != null || r.valorMaximo() != null)
                    .toList();
            if (!comFaixa.isEmpty() && comFaixa.size() < maisEspecificas.size()) {
                maisEspecificas = comFaixa;
            }
        }

        RegraItemRequest vencedora = maisEspecificas.get(0);
        BigDecimal taxaFinal = calcularTaxaFinal(vencedora, taxaBaseContratual);

        BigDecimal parcelaBase = valorVendaEscalado.multiply(taxaFinal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal parcelaBonusPerc = (vencedora.bonusPercentual() != null && vencedora.bonusPercentual().compareTo(BigDecimal.ZERO) > 0)
                ? valorVendaEscalado.multiply(vencedora.bonusPercentual()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal parcelaBonusFixo = (vencedora.bonusValorFixo() != null && vencedora.bonusValorFixo().compareTo(BigDecimal.ZERO) > 0)
                ? vencedora.bonusValorFixo().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorComissao = parcelaBase.add(parcelaBonusPerc).add(parcelaBonusFixo);

        Long regraId = extrairIdNumerico(vencedora.blocoId());
        boolean mudouFaixa = false;
        if (cenario100Referencia != null) {
            mudouFaixa = !Objects.equals(regraId, cenario100Referencia.regraId())
                    || !Objects.equals("REGRA_PROPOSTA", cenario100Referencia.origemTaxa());
        }

        return new VendaSimuladaCenarioItemDTO(
                fatorEscala,
                valorVendaEscalado,
                regraId,
                vencedora.nome(),
                "REGRA_PROPOSTA",
                true,
                taxaFinal,
                parcelaBonusPerc,
                parcelaBonusFixo,
                valorComissao,
                mudouFaixa
        );
    }

    private BigDecimal calcularTaxaFinal(RegraItemRequest regra, BigDecimal taxaBase) {
        TipoOperacaoBase operacao = regra.tipoOperacao() != null
                ? regra.tipoOperacao()
                : TipoOperacaoBase.DEFINIR_TAXA;

        BigDecimal taxaCalculada;
        if (operacao == TipoOperacaoBase.DEFINIR_TAXA) {
            taxaCalculada = regra.taxa() != null ? regra.taxa() : regra.valorAjuste();
        } else {
            BigDecimal valorAjuste = regra.valorAjuste();
            if (valorAjuste == null || taxaBase == null) {
                return taxaBase != null ? taxaBase : BigDecimal.ZERO;
            }
            switch (operacao) {
                case ACRESCIMO_PONTOS -> taxaCalculada = taxaBase.add(valorAjuste);
                case DESCONTO_PONTOS -> taxaCalculada = taxaBase.subtract(valorAjuste);
                case MULTIPLICADOR_BASE -> taxaCalculada = taxaBase.multiply(valorAjuste);
                case DIVISOR_BASE -> {
                    if (valorAjuste.compareTo(BigDecimal.ZERO) == 0) {
                        taxaCalculada = taxaBase;
                    } else {
                        taxaCalculada = taxaBase.divide(valorAjuste, 4, RoundingMode.HALF_UP);
                    }
                }
                default -> taxaCalculada = taxaBase;
            }
        }

        if (taxaCalculada == null) {
            taxaCalculada = taxaBase != null ? taxaBase : BigDecimal.ZERO;
        }

        return taxaCalculada.setScale(4, RoundingMode.HALF_UP);
    }

    private CenarioConsolidadoDTO consolidarCenario(
            List<VendaSimuladaDetalheDTO> vendas,
            BigDecimal fatorEscala,
            String descricao,
            BigDecimal fatorProjecao,
            java.util.function.Function<VendaSimuladaDetalheDTO, VendaSimuladaCenarioItemDTO> extractor) {

        BigDecimal totalVendas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalComissaoBase = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalComissaoProposta = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int qtdMudancaFaixa = 0;
        int qtdRegraAplicada = 0;
        int qtdFallbackBase = 0;

        for (VendaSimuladaDetalheDTO v : vendas) {
            VendaSimuladaCenarioItemDTO item = extractor.apply(v);
            if (item != null) {
                totalVendas = totalVendas.add(item.valorVenda());
                totalComissaoProposta = totalComissaoProposta.add(item.valorComissao());

                BigDecimal comissaoBaseEscalada = item.valorVenda().multiply(v.taxaBaseContratual()).setScale(2, RoundingMode.HALF_UP);
                totalComissaoBase = totalComissaoBase.add(comissaoBaseEscalada);

                if (item.mudouFaixaEmRelacaoA100()) {
                    qtdMudancaFaixa++;
                }
                if ("REGRA_PROPOSTA".equals(item.origemTaxa())) {
                    qtdRegraAplicada++;
                } else {
                    qtdFallbackBase++;
                }
            }
        }

        BigDecimal deltaComissao = totalComissaoProposta.subtract(totalComissaoBase).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deltaPercentual = totalComissaoBase.compareTo(BigDecimal.ZERO) > 0
                ? deltaComissao.multiply(BigDecimal.valueOf(100)).divide(totalComissaoBase, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxaMediaEfetivaBase = totalVendas.compareTo(BigDecimal.ZERO) > 0
                ? totalComissaoBase.divide(totalVendas, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        BigDecimal taxaMediaEfetivaProposta = totalVendas.compareTo(BigDecimal.ZERO) > 0
                ? totalComissaoProposta.divide(totalVendas, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        BigDecimal comissaoProjetada = totalComissaoProposta.multiply(fatorProjecao).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deltaProjetado = deltaComissao.multiply(fatorProjecao).setScale(2, RoundingMode.HALF_UP);

        return new CenarioConsolidadoDTO(
                fatorEscala,
                descricao,
                totalVendas,
                totalComissaoBase,
                totalComissaoProposta,
                deltaComissao,
                deltaPercentual,
                taxaMediaEfetivaBase,
                taxaMediaEfetivaProposta,
                comissaoProjetada,
                deltaProjetado,
                qtdMudancaFaixa,
                qtdRegraAplicada,
                qtdFallbackBase
        );
    }

    private String gerarMemoriaCalculoItem(
            Sale sale,
            VendaSimuladaCenarioItemDTO item100,
            VendaSimuladaCenarioItemDTO item80,
            VendaSimuladaCenarioItemDTO item120,
            VendaSimuladaCenarioItemDTO item150,
            BigDecimal taxaBaseContratual,
            BigDecimal comissaoBase100,
            BigDecimal deltaComissao100,
            List<String> impedimentos) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(LOCALE_PT_BR, "Venda ID %s em %s. ", sale.getId(), sale.getSaleDate()));
        sb.append(String.format(LOCALE_PT_BR, "Canal: %s. Valor Bruto Original: R$ %,.2f. ",
                sale.getSaleChannel() != null ? sale.getSaleChannel() : "PADRAO",
                sale.getValue() != null ? sale.getValue() : BigDecimal.ZERO));

        sb.append(String.format(LOCALE_PT_BR, "Taxa Base Contratual (tb_basecomiss): %,.2f%% (Comissão Base: R$ %,.2f). ",
                taxaBaseContratual.multiply(BigDecimal.valueOf(100)),
                comissaoBase100));

        if ("REGRA_PROPOSTA".equals(item100.origemTaxa())) {
            sb.append(String.format(LOCALE_PT_BR, "Cenário 100%% (Proposta): Aplicada a regra promocional '%s' (ID %s). Taxa revisada: %,.2f%%. ",
                    item100.regraNome(),
                    item100.regraId() != null ? item100.regraId() : "N/D",
                    item100.taxaAplicada().multiply(BigDecimal.valueOf(100))));
            if (item100.valorBonusPerc().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(String.format(LOCALE_PT_BR, "Bônus percentual: R$ %,.2f. ", item100.valorBonusPerc()));
            }
            if (item100.valorBonusFixo().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(String.format(LOCALE_PT_BR, "Bônus fixo: R$ %,.2f. ", item100.valorBonusFixo()));
            }
            sb.append(String.format(LOCALE_PT_BR, "Comissão Proposta: R$ %,.2f (Impacto: %sR$ %,.2f). ",
                    item100.valorComissao(),
                    deltaComissao100.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "",
                    deltaComissao100));
        } else {
            sb.append(String.format(LOCALE_PT_BR, "Cenário 100%% (Proposta): Nenhuma regra da proposta cobriu a faixa de valor; aplicado fallback na taxa base contratual (%,.2f%%). Comissão: R$ %,.2f. ",
                    taxaBaseContratual.multiply(BigDecimal.valueOf(100)),
                    item100.valorComissao()));
        }

        sb.append(String.format(LOCALE_PT_BR, "Sensibilidade: [80%%: R$ %,.2f (Taxa %,.2f%%, Comiss R$ %,.2f%s) | ",
                item80.valorVenda(),
                item80.taxaAplicada().multiply(BigDecimal.valueOf(100)),
                item80.valorComissao(),
                item80.mudouFaixaEmRelacaoA100() ? ", MUDOU DE FAIXA" : ""));
        sb.append(String.format(LOCALE_PT_BR, "120%%: R$ %,.2f (Taxa %,.2f%%, Comiss R$ %,.2f%s) | ",
                item120.valorVenda(),
                item120.taxaAplicada().multiply(BigDecimal.valueOf(100)),
                item120.valorComissao(),
                item120.mudouFaixaEmRelacaoA100() ? ", MUDOU DE FAIXA" : ""));
        sb.append(String.format(LOCALE_PT_BR, "150%%: R$ %,.2f (Taxa %,.2f%%, Comiss R$ %,.2f%s)]. ",
                item150.valorVenda(),
                item150.taxaAplicada().multiply(BigDecimal.valueOf(100)),
                item150.valorComissao(),
                item150.mudouFaixaEmRelacaoA100() ? ", MUDOU DE FAIXA" : ""));

        if (impedimentos != null && !impedimentos.isEmpty()) {
            sb.append("Avisos/Impedimentos cadastrais: ").append(String.join("; ", impedimentos)).append(".");
        } else {
            sb.append("Venda regular e elegível para apuração.");
        }

        return sb.toString();
    }

    private String gerarMemoriaCalculoConsolidada(
            String competencia,
            Campanha campanha,
            LocalDate dataInicio,
            LocalDate dataFim,
            long diasInclusivos,
            BigDecimal fatorProjecao,
            int totalVendas,
            BigDecimal valorTotalRecortado,
            CenarioConsolidadoDTO cenario100,
            CenarioConsolidadoDTO cenario80,
            CenarioConsolidadoDTO cenario120,
            CenarioConsolidadoDTO cenario150) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(LOCALE_PT_BR, "Simulação consolidada para a competência %s. ", competencia));
        if (campanha != null) {
            sb.append(String.format(LOCALE_PT_BR, "Campanha vinculada: '%s' (ID %d). ", campanha.getTitulo(), campanha.getId()));
        } else {
            sb.append("Regras avulsas sem campanha previamente vinculada. ");
        }
        sb.append(String.format(LOCALE_PT_BR, "Premissas de vigência: %s a %s (%d dias inclusivos). ",
                dataInicio, dataFim, diasInclusivos));
        sb.append(String.format(LOCALE_PT_BR, "Fator de projeção temporal mensal: %d ÷ 30 = %,.6f. ",
                diasInclusivos, fatorProjecao));
        sb.append(String.format(LOCALE_PT_BR, "Universo recortado: %d vendas totalizando R$ %,.2f. ",
                totalVendas, valorTotalRecortado));

        sb.append(String.format(LOCALE_PT_BR, "Cenário 100%% (Proposta vs Base): Comissão Base Contratual = R$ %,.2f (Taxa média: %,.2f%%); ",
                cenario100.totalComissaoBase(),
                cenario100.taxaMediaEfetivaBase().multiply(BigDecimal.valueOf(100))));
        sb.append(String.format(LOCALE_PT_BR, "Comissão Proposta Revisada = R$ %,.2f (Taxa média: %,.2f%%); ",
                cenario100.totalComissaoProposta(),
                cenario100.taxaMediaEfetivaProposta().multiply(BigDecimal.valueOf(100))));
        sb.append(String.format(LOCALE_PT_BR, "Delta financeiro mensal = %sR$ %,.2f (%s%,.2f%%); ",
                cenario100.deltaComissao().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "",
                cenario100.deltaComissao(),
                cenario100.deltaPercentual().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "",
                cenario100.deltaPercentual()));
        sb.append(String.format(LOCALE_PT_BR, "Comissão projetada no período da campanha = R$ %,.2f (Impacto projetado: %sR$ %,.2f). ",
                cenario100.comissaoProjetada(),
                cenario100.deltaProjetado().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "",
                cenario100.deltaProjetado()));
        sb.append(String.format(LOCALE_PT_BR, "Distribuição das vendas: %d com regra promocional aplicada, %d com fallback na base contratual. ",
                cenario100.qtdVendasComRegraAplicada(),
                cenario100.qtdVendasComFallbackBase()));

        sb.append(String.format(LOCALE_PT_BR, "Sensibilidade Financeira: Cenário 80%% = R$ %,.2f comissão (%d mudanças de faixa); ",
                cenario80.totalComissaoProposta(), cenario80.qtdVendasComMudancaFaixa()));
        sb.append(String.format(LOCALE_PT_BR, "Cenário 120%% = R$ %,.2f comissão (%d mudanças de faixa); ",
                cenario120.totalComissaoProposta(), cenario120.qtdVendasComMudancaFaixa()));
        sb.append(String.format(LOCALE_PT_BR, "Cenário 150%% = R$ %,.2f comissão (%d mudanças de faixa). ",
                cenario150.totalComissaoProposta(), cenario150.qtdVendasComMudancaFaixa()));
        sb.append("Cálculo 100% determinístico e auditável, sem dependência de chamadas à IA.");

        return sb.toString();
    }

    private SimulacaoCenariosResponse construirRespostaVazia(
            String competenciaStr,
            Campanha campanha,
            LocalDate dataInicio,
            LocalDate dataFim,
            long diasInclusivos,
            BigDecimal fatorProjecao,
            List<String> avisos) {

        CenarioConsolidadoDTO vazio100 = criarCenarioVazio(FATOR_100, "Cenário 100% (Proposta Revisada)");
        CenarioConsolidadoDTO vazio80 = criarCenarioVazio(FATOR_80, "Cenário 80% (Volume Reduzido)");
        CenarioConsolidadoDTO vazio120 = criarCenarioVazio(FATOR_120, "Cenário 120% (Crescimento Otimista)");
        CenarioConsolidadoDTO vazio150 = criarCenarioVazio(FATOR_150, "Cenário 150% (Alta Performance)");

        return new SimulacaoCenariosResponse(
                null,
                null,
                competenciaStr,
                0,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                campanha != null ? campanha.getId() : null,
                campanha != null ? campanha.getTitulo() : "Sem Campanha",
                dataInicio,
                dataFim,
                diasInclusivos,
                fatorProjecao,
                vazio100,
                vazio80,
                vazio120,
                vazio150,
                0,
                List.of(),
                avisos,
                "Nenhuma venda da competência pertence ao público-alvo das regras avaliadas. Não houve cálculo a consolidar."
        );
    }

    private CenarioConsolidadoDTO criarCenarioVazio(BigDecimal fator, String descricao) {
        BigDecimal zero2 = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal zero4 = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        return new CenarioConsolidadoDTO(fator, descricao, zero2, zero2, zero2, zero2, zero2, zero4, zero4, zero2, zero2, 0, 0, 0);
    }

    private List<RegraItemRequest> resolverRegras(SimulacaoCenariosRequest request, Campanha campanha) {
        if (request.regras() != null && !request.regras().isEmpty()) {
            return request.regras();
        }
        if (campanha != null) {
            return referenciaPublicoService.resolverRegras(new RecortePublicoRequest(
                    request.competencia(),
                    campanha.getId(),
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        return List.of();
    }

    private Optional<BigDecimal> buscarTaxaBase(Brand brand, Position position, LocalDate saleDate, List<BaseComiss> bases) {
        if (brand == null || position == null || saleDate == null || bases == null || bases.isEmpty()) {
            return Optional.empty();
        }
        LocalDate mesRef = saleDate.withDayOfMonth(1);
        if (brand.getId() != null && position.getId() != null) {
            Optional<BaseComiss> opt = bases.stream()
                    .filter(b -> b.getBrand() != null && brand.getId().equals(b.getBrand().getId())
                            && b.getPosition() != null && position.getId().equals(b.getPosition().getId())
                            && mesRef.equals(b.getReferenceMonth()))
                    .findFirst();
            if (opt.isPresent()) return opt.map(BaseComiss::getPercentage);

            opt = bases.stream()
                    .filter(b -> b.getBrand() != null && brand.getId().equals(b.getBrand().getId())
                            && b.getPosition() != null && position.getId().equals(b.getPosition().getId()))
                    .findFirst();
            if (opt.isPresent()) return opt.map(BaseComiss::getPercentage);
        }
        if (brand.getCode() != null && position.getCode() != null) {
            Optional<BaseComiss> opt = bases.stream()
                    .filter(b -> b.getBrand() != null && brand.getCode().equals(b.getBrand().getCode())
                            && b.getPosition() != null && position.getCode().equals(b.getPosition().getCode())
                            && mesRef.equals(b.getReferenceMonth()))
                    .findFirst();
            if (opt.isPresent()) return opt.map(BaseComiss::getPercentage);

            return bases.stream()
                    .filter(b -> b.getBrand() != null && brand.getCode().equals(b.getBrand().getCode())
                            && b.getPosition() != null && position.getCode().equals(b.getPosition().getCode()))
                    .findFirst()
                    .map(BaseComiss::getPercentage);
        }
        return Optional.empty();
    }

    private Long extrairIdNumerico(String blocoId) {
        if (blocoId == null || blocoId.isBlank()) return null;
        try {
            String digitos = blocoId.replaceAll("\\D+", "");
            if (!digitos.isBlank()) {
                return Long.parseLong(digitos);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String normalizarCompetencia(String comp) {
        if (comp == null || comp.isBlank()) {
            throw new BusinessException("A competência é obrigatória para a simulação.");
        }
        String limpo = comp.trim();
        if (limpo.length() >= 7) {
            return limpo.substring(0, 7);
        }
        throw new BusinessException(String.format("Formato de competência inválido: '%s'. Utilize YYYY-MM.", comp));
    }

    private SimulacaoResumoDTO converterParaResumoDTO(Simulacao s) {
        return new SimulacaoResumoDTO(
                s.getId(),
                s.getProtocolo(),
                s.getExecutadoEm(),
                s.getCompetencia(),
                s.getCampanha() != null ? s.getCampanha().getId() : null,
                s.getTituloCampanha(),
                s.getDataInicioCampanha(),
                s.getDataFimCampanha(),
                s.getDiasInclusivos(),
                s.getFatorProjecao(),
                s.getTotalVendasRecortadas(),
                s.getValorTotalRecortado(),
                extrairCenario100(s),
                extrairCenario80(s),
                extrairCenario120(s),
                extrairCenario150(s)
        );
    }

    private VendaSimuladaDetalheDTO converterParaVendaDetalheDTO(SimulacaoVenda sv) {
        VendaSimuladaCenarioItemDTO c100 = new VendaSimuladaCenarioItemDTO(
                FATOR_100,
                sv.getValorVendaOriginal(),
                sv.getRegraId100(),
                sv.getRegraNome100(),
                sv.getOrigemTaxa100(),
                sv.isAtendeFaixa100(),
                sv.getTaxaAplicada100(),
                sv.getValorBonusPerc100(),
                sv.getValorBonusFixo100(),
                sv.getValorComissao100(),
                false
        );

        VendaSimuladaCenarioItemDTO c80 = new VendaSimuladaCenarioItemDTO(
                FATOR_80,
                sv.getValor80(),
                sv.getRegraId80(),
                sv.getRegraNome80(),
                sv.getOrigem80(),
                true,
                sv.getTaxa80(),
                sv.getBonusPerc80(),
                sv.getBonusFixo80(),
                sv.getComissao80(),
                sv.isMudouFaixa80()
        );

        VendaSimuladaCenarioItemDTO c120 = new VendaSimuladaCenarioItemDTO(
                FATOR_120,
                sv.getValor120(),
                sv.getRegraId120(),
                sv.getRegraNome120(),
                sv.getOrigem120(),
                true,
                sv.getTaxa120(),
                sv.getBonusPerc120(),
                sv.getBonusFixo120(),
                sv.getComissao120(),
                sv.isMudouFaixa120()
        );

        VendaSimuladaCenarioItemDTO c150 = new VendaSimuladaCenarioItemDTO(
                FATOR_150,
                sv.getValor150(),
                sv.getRegraId150(),
                sv.getRegraNome150(),
                sv.getOrigem150(),
                true,
                sv.getTaxa150(),
                sv.getBonusPerc150(),
                sv.getBonusFixo150(),
                sv.getComissao150(),
                sv.isMudouFaixa150()
        );

        List<String> impedimentos = (sv.getImpedimentos() != null && !sv.getImpedimentos().isBlank())
                ? Arrays.asList(sv.getImpedimentos().split(";\\n?"))
                : Collections.emptyList();

        return new VendaSimuladaDetalheDTO(
                sv.getIdVenda(),
                sv.getMatricula(),
                sv.getCodCargo(),
                sv.getDescriCargo(),
                sv.getCodLoja(),
                sv.getCodMarca(),
                sv.getDescrMarca(),
                sv.getCanal(),
                sv.getDataVenda(),
                sv.getValorVendaOriginal(),
                sv.getTaxaBaseContratual(),
                sv.getValorComissaoBase(),
                c100,
                c80,
                c120,
                c150,
                sv.getDeltaComissao100(),
                sv.isValida(),
                impedimentos,
                sv.getMemoriaCalculoItem()
        );
    }

    private CenarioConsolidadoDTO extrairCenario100(Simulacao s) {
        return new CenarioConsolidadoDTO(
                FATOR_100,
                "Cenário 100% (Proposta Revisada)",
                s.getValorTotalRecortado(),
                s.getTotalComissaoBase(),
                s.getTotalComissaoProposta(),
                s.getDeltaComissao(),
                s.getDeltaPercentual(),
                s.getTaxaMediaEfetivaBase(),
                s.getTaxaMediaEfetivaProposta(),
                s.getComissaoProjetada(),
                s.getDeltaProjetado(),
                s.getQtdVendasComMudancaFaixa(),
                s.getQtdVendasComRegraAplicada(),
                s.getQtdVendasComFallbackBase()
        );
    }

    private CenarioConsolidadoDTO extrairCenario80(Simulacao s) {
        return new CenarioConsolidadoDTO(
                FATOR_80,
                "Cenário 80% (Volume Reduzido)",
                s.getVendas80(),
                s.getComissaoBase80(),
                s.getComissaoProposta80(),
                s.getDelta80(),
                s.getDeltaPerc80(),
                s.getTaxaMediaEfetivaBase(),
                s.getTaxaMedia80(),
                s.getProjetada80(),
                s.getDeltaProj80(),
                s.getMudancaFaixa80(),
                s.getQtdVendasComRegraAplicada(),
                s.getQtdVendasComFallbackBase()
        );
    }

    private CenarioConsolidadoDTO extrairCenario120(Simulacao s) {
        return new CenarioConsolidadoDTO(
                FATOR_120,
                "Cenário 120% (Crescimento Otimista)",
                s.getVendas120(),
                s.getComissaoBase120(),
                s.getComissaoProposta120(),
                s.getDelta120(),
                s.getDeltaPerc120(),
                s.getTaxaMediaEfetivaBase(),
                s.getTaxaMedia120(),
                s.getProjetada120(),
                s.getDeltaProj120(),
                s.getMudancaFaixa120(),
                s.getQtdVendasComRegraAplicada(),
                s.getQtdVendasComFallbackBase()
        );
    }

    private CenarioConsolidadoDTO extrairCenario150(Simulacao s) {
        return new CenarioConsolidadoDTO(
                FATOR_150,
                "Cenário 150% (Alta Performance)",
                s.getVendas150(),
                s.getComissaoBase150(),
                s.getComissaoProposta150(),
                s.getDelta150(),
                s.getDeltaPerc150(),
                s.getTaxaMediaEfetivaBase(),
                s.getTaxaMedia150(),
                s.getProjetada150(),
                s.getDeltaProj150(),
                s.getMudancaFaixa150(),
                s.getQtdVendasComRegraAplicada(),
                s.getQtdVendasComFallbackBase()
        );
    }
}
