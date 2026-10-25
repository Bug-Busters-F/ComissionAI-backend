package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.calculo.CalculoComissaoResponse;
import com.bugbusters.backend.dto.calculo.CalculoCompetenciaResponseDTO;
import com.bugbusters.backend.dto.calculo.CalculoImpedimentoDTO;
import com.bugbusters.backend.dto.calculo.CalculoIndividualResponseDTO;
import com.bugbusters.backend.dto.calculo.CalculoItemResponseDTO;
import com.bugbusters.backend.dto.calculo.LogCalculoResponse;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.LogCalculoSpecification;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

/**
 * Serviço responsável pelo processamento do cálculo de comissões, abrangendo:
 * 1. Apuração individual de vendas persistidas (S1-B07);
 * 2. Apuração consolidada de vendas por competência mensal;
 * 3. Seleção de taxas/regras conforme S1-B08 (prioridade Regra ativa, fallback BaseComiss);
 * 4. Validação de vínculos essenciais (RH, marcas, lojas, cargos, vigência);
 * 5. Política rigorosa de precisão financeira decimal e arredondamento (ABNT NBR 5891 / HALF_UP);
 * 6. Proteção contra cálculos duplicados e concorrência (Idempotência);
 * 7. Registro de logs imutáveis para auditoria financeira (S1-B10).
 */
@Service
public class CalculoService {

    private static final Logger log = LoggerFactory.getLogger(CalculoService.class);
    private static final BigDecimal TAXA_PADRAO = new BigDecimal("0.1000");
    private static final Long REGRA_PADRAO_ID = 1L;

    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;
    private final RegraRepository regraRepository;
    private final SaleRepository saleRepository;
    private final TaxaComissaoResolver taxaComissaoResolver;
    private final CoberturaBaseService coberturaBaseService;
    private final com.bugbusters.backend.service.lock.TravaCompetencia travaCompetencia;
    private final RegistrationRepository registrationRepository;
    private final BrandRepository brandRepository;
    private final StoreRepository storeRepository;
    private final com.bugbusters.backend.repository.FechamentoCompetenciaRepository fechamentoCompetenciaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository,
                          SaleRepository saleRepository,
                          TaxaComissaoResolver taxaComissaoResolver,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) CoberturaBaseService coberturaBaseService,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) com.bugbusters.backend.service.lock.TravaCompetencia travaCompetencia,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) RegistrationRepository registrationRepository,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) BrandRepository brandRepository,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) StoreRepository storeRepository,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) com.bugbusters.backend.repository.FechamentoCompetenciaRepository fechamentoCompetenciaRepository) {
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
        this.regraRepository = regraRepository;
        this.saleRepository = saleRepository;
        this.taxaComissaoResolver = taxaComissaoResolver;
        this.coberturaBaseService = coberturaBaseService;
        this.travaCompetencia = travaCompetencia;
        this.registrationRepository = registrationRepository;
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.fechamentoCompetenciaRepository = fechamentoCompetenciaRepository;
    }

    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository,
                          SaleRepository saleRepository,
                          TaxaComissaoResolver taxaComissaoResolver,
                          CoberturaBaseService coberturaBaseService,
                          com.bugbusters.backend.service.lock.TravaCompetencia travaCompetencia,
                          RegistrationRepository registrationRepository,
                          BrandRepository brandRepository,
                          StoreRepository storeRepository) {
        this(resultadoCalculoRepository, logCalculoRepository, regraRepository, saleRepository, taxaComissaoResolver, coberturaBaseService, travaCompetencia, registrationRepository, brandRepository, storeRepository, null);
    }

    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository,
                          SaleRepository saleRepository,
                          TaxaComissaoResolver taxaComissaoResolver,
                          CoberturaBaseService coberturaBaseService,
                          com.bugbusters.backend.service.lock.TravaCompetencia travaCompetencia) {
        this(resultadoCalculoRepository, logCalculoRepository, regraRepository, saleRepository, taxaComissaoResolver, coberturaBaseService, travaCompetencia, null, null, null, null);
    }

    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository,
                          SaleRepository saleRepository,
                          TaxaComissaoResolver taxaComissaoResolver,
                          CoberturaBaseService coberturaBaseService) {
        this(resultadoCalculoRepository, logCalculoRepository, regraRepository, saleRepository, taxaComissaoResolver, coberturaBaseService, null, null, null, null, null);
    }

    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository,
                          SaleRepository saleRepository,
                          TaxaComissaoResolver taxaComissaoResolver,
                          RegistrationRepository registrationRepository,
                          BrandRepository brandRepository,
                          StoreRepository storeRepository) {
        this(resultadoCalculoRepository, logCalculoRepository, regraRepository, saleRepository, taxaComissaoResolver, null, null, registrationRepository, brandRepository, storeRepository, null);
    }

    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository,
                          SaleRepository saleRepository,
                          TaxaComissaoResolver taxaComissaoResolver) {
        this(resultadoCalculoRepository, logCalculoRepository, regraRepository, saleRepository, taxaComissaoResolver, null, null, null, null, null);
    }

    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository) {
        this(resultadoCalculoRepository, logCalculoRepository, regraRepository, null, null, null, null, null, null, null);
    }

    /**
     * Processa o cálculo de comissão para uma venda individual persistida em S1-B07.
     *
     * Valida os vínculos essenciais (matrícula ativa, cargo, marca, loja, valor),
     * resolve a taxa conforme S1-B08 (Regra ativa prioritária, BaseComiss como fallback)
     * e aplica a política de precisão decimal (escala 2, RoundingMode.HALF_UP).
     *
     * Persiste o resultado em tb_resultado_calculo e o log de auditoria em tb_log_calculo_imutavel (S1-B10).
     * Em caso de impedimento de negócio, retorna status IMPEDIDO com o motivo contratual.
     */
    @Transactional
    public CalculoIndividualResponseDTO calcularVendaIndividualPorId(UUID idVenda) {
        if (idVenda == null) {
            throw new BusinessException("O ID da venda é obrigatório para o cálculo individual.");
        }

        if (saleRepository == null) {
            throw new IllegalStateException("SaleRepository não inicializado no serviço de cálculo.");
        }

        Sale sale = saleRepository.findById(idVenda)
                .orElseThrow(() -> new BusinessException("Venda não encontrada com o ID: " + idVenda));

        if (sale.getSaleDate() != null && travaCompetencia != null)
            travaCompetencia.adquirirTrava(YearMonth.from(sale.getSaleDate()).toString());

        // 1. Verifica se já foi calculada (Idempotência)
        Optional<ResultadoCalculo> existenteOpt = resultadoCalculoRepository.findByIdVenda(idVenda);
        if (existenteOpt.isPresent()) {
            ResultadoCalculo existente = existenteOpt.get();
            if (sale.getValue().compareTo(existente.getValorVenda()) != 0) {
                throw new BusinessException(String.format(
                        "Solicitação rejeitada por duplicidade com dados divergentes. A venda com ID '%s' já foi calculada com valor %s (valor atual: %s).",
                        idVenda, existente.getValorVenda(), sale.getValue()
                ));
            }
            log.info("Idempotência aplicada para cálculo da venda ID {}: retornando resultado prévio.", idVenda);
            return CalculoIndividualResponseDTO.sucesso(
                    existente.getProtocoloCalculo(),
                    existente.getIdVenda(),
                    existente.getMatricula(),
                    existente.getCodCargo(),
                    existente.getCodMarca(),
                    existente.getCodLoja(),
                    existente.getDataVenda(),
                    existente.getValorVenda(),
                    existente.getTaxaAplicada(),
                    existente.getValorComissao(),
                    existente.getRegraId(),
                    existente.getOrigemTaxa(),
                    existente.getCalculadoEm()
            );
        }

        validarNovoCalculoPermitido(sale.getSaleDate());

        // 2. Validação rigorosa de cobertura de taxa base ANTES de qualquer cálculo ou escrita
        if (coberturaBaseService != null) {
            coberturaBaseService.validarCobertura(sale);
        }

        // 3. Valida vínculos essenciais e seleciona taxa (S1-B08)
        ResolucaoTaxaResult resolucao = taxaComissaoResolver != null
                ? taxaComissaoResolver.resolverTaxa(sale)
                : ResolucaoTaxaResult.sucesso(TAXA_PADRAO, REGRA_PADRAO_ID, "PADRAO");

        if (!resolucao.sucesso()) {
            String matricula = sale.getRegistration() != null ? sale.getRegistration().getRegistration() : null;
            log.warn("Impedimento detectado no cálculo da venda ID {}: {}", idVenda, resolucao.motivoImpedimento());
            return CalculoIndividualResponseDTO.impedido(
                    idVenda,
                    matricula,
                    sale.getSaleDate(),
                    sale.getValue(),
                    resolucao.motivoImpedimento()
            );
        }

        BigDecimal taxaAplicada = resolucao.taxa();
        Long idRegra = resolucao.idRegra() != null ? resolucao.idRegra() : REGRA_PADRAO_ID;
        garantirRegraPadraoExistente(idRegra, taxaAplicada);

        // 3. Aplica percentual básico com precisão decimal e arredondamento HALF_UP
        ComissaoCalculada calculada = calcularComissaoTotal(sale.getValue(), taxaAplicada, resolucao);
        BigDecimal comissao = calculada.total();
        UUID protocolo = UUID.randomUUID();

        if (travaCompetencia != null && sale.getSaleDate() != null) {
            travaCompetencia.adquirirTrava(java.time.YearMonth.from(sale.getSaleDate()).toString());
        }

        Integer codCargo = sale.getRegistration() != null && sale.getRegistration().getPosition() != null
                ? sale.getRegistration().getPosition().getCode() : null;
        Integer codMarca = sale.getBrand() != null ? sale.getBrand().getCode() : null;
        Integer codLoja = sale.getStore() != null ? sale.getStore().getCode() : null;
        String matricula = sale.getRegistration() != null ? sale.getRegistration().getRegistration() : null;

        ResultadoCalculo novoResultado = new ResultadoCalculo(
                protocolo,
                idVenda,
                matricula,
                codMarca,
                codLoja,
                codCargo,
                idRegra,
                sale.getSaleDate(),
                sale.getValue(),
                taxaAplicada,
                comissao,
                "INDIVIDUAL"
        );
        novoResultado.setOrigemTaxa(resolucao.origemTaxa());
        novoResultado.setValorBonusPercentual(calculada.bonusPercentual());
        novoResultado.setValorBonusFixo(calculada.bonusFixo());

        try {
            ResultadoCalculo salvo = resultadoCalculoRepository.save(novoResultado);

            // 4. Log imutável de auditoria (S1-B10 / BUG-23)
            String tipoVenda = sale.getTipoVenda() != null && !sale.getTipoVenda().isBlank() ? sale.getTipoVenda() : "INFORMADA";
            String idLoteOrigem = sale.getIdLoteOrigem();
            String versaoRegra = resolucao.versaoOuReferenciaRegra() != null ? resolucao.versaoOuReferenciaRegra() : "REGRA#" + idRegra;
            String parametrosSnapshot = montarSnapshotParametros(
                    sale.getValue(), taxaAplicada, comissao, idRegra, resolucao.origemTaxa(), versaoRegra,
                    sale.getSaleDate(), matricula, codCargo, codLoja, codMarca,
                    sale.getSaleChannel() != null ? sale.getSaleChannel() : "PADRAO",
                    tipoVenda, idLoteOrigem, calculada
            );

            LogCalculoImutavel logImutavel = new LogCalculoImutavel(
                    salvo.getProtocoloCalculo(),
                    idVenda,
                    matricula,
                    codCargo,
                    codLoja,
                    codMarca,
                    sale.getValue(),
                    taxaAplicada,
                    comissao,
                    idRegra,
                    sale.getSaleDate(),
                    sale.getSaleChannel() != null ? sale.getSaleChannel() : "PADRAO",
                    "MOTOR_PRODUCAO",
                    tipoVenda,
                    idLoteOrigem,
                    versaoRegra,
                    parametrosSnapshot
            );
            logImutavel.setOrigemTaxa(resolucao.origemTaxa());
            logImutavel.setValorBonusPercentual(calculada.bonusPercentual());
            logImutavel.setValorBonusFixo(calculada.bonusFixo());
            logCalculoRepository.save(logImutavel);

            return CalculoIndividualResponseDTO.sucesso(
                    salvo.getProtocoloCalculo(),
                    idVenda,
                    matricula,
                    codCargo,
                    codMarca,
                    codLoja,
                    sale.getSaleDate(),
                    sale.getValue(),
                    taxaAplicada,
                    comissao,
                    idRegra,
                    resolucao.origemTaxa(),
                    salvo.getCalculadoEm()
            );
        } catch (DataIntegrityViolationException ex) {
            log.warn("Violação de integridade por concorrência detectada para cálculo da venda ID {}. Recuperando resultado existente.", idVenda);
            ResultadoCalculo concorrente = resultadoCalculoRepository.findByIdVenda(idVenda)
                    .orElseThrow(() -> ex);

            if (sale.getValue().compareTo(concorrente.getValorVenda()) != 0) {
                throw new BusinessException(String.format(
                        "Solicitação rejeitada por duplicidade com dados divergentes. A venda com ID '%s' já foi calculada com valor %s (valor atual: %s).",
                        idVenda, concorrente.getValorVenda(), sale.getValue()
                ));
            }

            return CalculoIndividualResponseDTO.sucesso(
                    concorrente.getProtocoloCalculo(),
                    concorrente.getIdVenda(),
                    concorrente.getMatricula(),
                    concorrente.getCodCargo(),
                    concorrente.getCodMarca(),
                    concorrente.getCodLoja(),
                    concorrente.getDataVenda(),
                    concorrente.getValorVenda(),
                    concorrente.getTaxaAplicada(),
                    concorrente.getValorComissao(),
                    concorrente.getRegraId(),
                    concorrente.getOrigemTaxa(),
                    concorrente.getCalculadoEm()
            );
        }
    }

    /**
     * Processa o cálculo de comissões para todo o conjunto de vendas efetivadas da competência (mês/ano).
     *
     * Identifica as vendas no intervalo da competência, valida os vínculos essenciais (S1-B08),
     * aplica o percentual básico/regra com precisão decimal e política de arredondamento HALF_UP,
     * persiste entradas, resultados e logs imutáveis (S1-B10) e retorna a lista consolidada de sucessos e impedimentos.
     */
    @Transactional
    public CalculoCompetenciaResponseDTO calcularPorCompetencia(String competenciaStr) {
        return calcularPorCompetencia(competenciaStr, false);
    }

    /**
     * Processa uma competência preservando a idempotência por padrão ou
     * recalculando as vendas com as regras vigentes quando solicitado.
     */
    @Transactional
    public CalculoCompetenciaResponseDTO calcularPorCompetencia(String competenciaStr, boolean recalcular) {
        return processarCompetencia(competenciaStr, recalcular, null, null);
    }

    /**
     * Processa a apuração oficial do fechamento final da competência (BACK-11).
     * Força o recálculo com a versão vigente mais recente para todas as vendas do período,
     * registra o vínculo com o fechamento e gera logs com origem de fechamento final.
     */
    @Transactional
    public CalculoCompetenciaResponseDTO processarCompetenciaFechamento(String competenciaStr, UUID fechamentoId, String executadoPor) {
        return processarCompetencia(competenciaStr, true, fechamentoId, executadoPor);
    }

    private CalculoCompetenciaResponseDTO processarCompetencia(
            String competenciaStr,
            boolean recalcular,
            UUID fechamentoId,
            String executadoPor
    ) {
        if (competenciaStr == null || competenciaStr.isBlank()) {
            throw new BusinessException("A competência é obrigatória para o cálculo em lote.");
        }

        if (saleRepository == null) {
            throw new IllegalStateException("SaleRepository não inicializado no serviço de cálculo.");
        }

        YearMonth ym;
        try {
            if (competenciaStr.length() == 7) {
                ym = YearMonth.parse(competenciaStr);
            } else {
                ym = YearMonth.from(LocalDate.parse(competenciaStr));
            }
        } catch (Exception e) {
            throw new BusinessException("Formato de competência inválido: '" + competenciaStr + "'. Utilize YYYY-MM ou YYYY-MM-DD.");
        }

        if (travaCompetencia != null) travaCompetencia.adquirirTrava(ym.toString());
        boolean historicoFechado = fechamentoId == null && fechamentoCompetenciaRepository != null &&
                fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(ym.toString(), com.bugbusters.backend.model.StatusFechamento.CONCLUIDO);
        boolean emFechamento = fechamentoId == null && fechamentoCompetenciaRepository != null &&
                fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(ym.toString(), com.bugbusters.backend.model.StatusFechamento.PROCESSANDO);
        if (historicoFechado && recalcular)
            throw new BusinessException("A competência " + ym + " já possui fechamento final concluído e está bloqueada para recálculo.");
        if (emFechamento) throw new BusinessException("A competência " + ym + " está em processo de fechamento final.");

        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();
        List<Sale> vendas = saleRepository.findBySaleDateBetweenOrderBySaleDateAsc(inicio, fim);
        if (!historicoFechado && coberturaBaseService != null && !vendas.isEmpty())
            coberturaBaseService.validarCobertura(vendas, ym);

        List<CalculoItemResponseDTO> sucessos = new ArrayList<>();
        List<CalculoImpedimentoDTO> impedimentos = new ArrayList<>();
        BigDecimal totalVendasCalculadas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalComissoesCalculadas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Otimização BACK-14: Pré-carregar contexto da competência (regras e bases) e resultados existentes
        TaxaComissaoResolver.ContextoCompetencia contexto = !historicoFechado && taxaComissaoResolver != null
                ? taxaComissaoResolver.carregarContexto(inicio, fim)
                : null;

        List<UUID> idsVendas = vendas.stream()
                .map(Sale::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, ResultadoCalculo> existentesPorIdVenda = Collections.emptyMap();
        if (!idsVendas.isEmpty()) {
            existentesPorIdVenda = resultadoCalculoRepository.findByIdVendaIn(idsVendas).stream()
                    .collect(Collectors.toMap(ResultadoCalculo::getIdVenda, Function.identity(), (a, b) -> a));
        }

        List<ResultadoCalculo> resultadosParaSalvar = new ArrayList<>();
        List<LogCalculoImutavel> logsParaSalvar = new ArrayList<>();

        for (Sale vendaPersistida : vendas) {
            Sale sale = recalcular ? bloquearVenda(vendaPersistida) : vendaPersistida;
            UUID idVenda = sale.getId();
            String matricula = sale.getRegistration() != null ? sale.getRegistration().getRegistration() : null;
            ResultadoCalculo existente = idVenda != null ? existentesPorIdVenda.get(idVenda) : null;
            if (existente == null && idVenda != null && resultadoCalculoRepository != null) {
                existente = resultadoCalculoRepository.findByIdVenda(idVenda).orElse(null);
            }

            // Sem recálculo, o resultado vigente é reutilizado sem consultar as regras atuais.
            if (existente != null && !recalcular) {
                if (sale.getValue().compareTo(existente.getValorVenda()) != 0) {
                    impedimentos.add(impedimentoPorDivergencia(idVenda, matricula, sale, existente));
                    continue;
                }

                sucessos.add(toItem(existente));
                totalVendasCalculadas = totalVendasCalculadas.add(existente.getValorVenda());
                totalComissoesCalculadas = totalComissoesCalculadas.add(existente.getValorComissao());
                continue;
            }

            if (historicoFechado)
                throw new BusinessException("A competência " + ym + " possui fechamento final concluído. Não é permitido calcular novas vendas nesse período.");

            ResolucaoTaxaResult resolucao = null;
            if (taxaComissaoResolver != null) {
                if (contexto != null) {
                    resolucao = taxaComissaoResolver.resolverTaxa(sale, contexto);
                }
                if (resolucao == null) {
                    resolucao = taxaComissaoResolver.resolverTaxa(sale);
                }
            } else {
                resolucao = ResolucaoTaxaResult.sucesso(TAXA_PADRAO, REGRA_PADRAO_ID, "PADRAO");
            }

            if (!resolucao.sucesso()) {
                impedimentos.add(new CalculoImpedimentoDTO(
                        idVenda,
                        matricula,
                        sale.getSaleDate(),
                        sale.getValue(),
                        resolucao.motivoImpedimento()
                ));
                continue;
            }

            BigDecimal taxaAplicada = resolucao.taxa();
            Long idRegra = resolucao.idRegra() != null ? resolucao.idRegra() : REGRA_PADRAO_ID;
            garantirRegraPadraoExistente(idRegra, taxaAplicada);

            ComissaoCalculada calculada = calcularComissaoTotal(sale.getValue(), taxaAplicada, resolucao);
            BigDecimal comissao = calculada.total();
            Integer codCargo = sale.getRegistration() != null && sale.getRegistration().getPosition() != null
                    ? sale.getRegistration().getPosition().getCode() : null;
            Integer codMarca = sale.getBrand() != null ? sale.getBrand().getCode() : null;
            Integer codLoja = sale.getStore() != null ? sale.getStore().getCode() : null;
            ResultadoCalculo resultado;

            if (existente != null) {
                resultado = atualizarResultadoExistente(
                        existente, idVenda, matricula, codMarca, codLoja, codCargo,
                        idRegra, sale, taxaAplicada, comissao, resolucao.origemTaxa()
                );
                resultado.setValorBonusPercentual(calculada.bonusPercentual());
                resultado.setValorBonusFixo(calculada.bonusFixo());
                resultado.setVersaoCampanhaNumero(resolucao.versaoCampanhaNumero());
                if (fechamentoId != null) {
                    resultado.setFechamentoId(fechamentoId);
                    resultado.setTipoCalculo("FECHAMENTO_FINAL");
                }
            } else {
                resultado = new ResultadoCalculo(
                        UUID.randomUUID(),
                        idVenda,
                        matricula,
                        codMarca,
                        codLoja,
                        codCargo,
                        idRegra,
                        sale.getSaleDate(),
                        sale.getValue(),
                        taxaAplicada,
                        comissao,
                        fechamentoId != null ? "FECHAMENTO_FINAL" : "COMPETENCIA"
                );
                resultado.setOrigemTaxa(resolucao.origemTaxa());
                resultado.setValorBonusPercentual(calculada.bonusPercentual());
                resultado.setValorBonusFixo(calculada.bonusFixo());
                resultado.setVersaoCampanhaNumero(resolucao.versaoCampanhaNumero());
                if (fechamentoId != null) {
                    resultado.setFechamentoId(fechamentoId);
                }
            }

            resultadosParaSalvar.add(resultado);
            String origemLog = fechamentoId != null ? "FECHAMENTO_FINAL" : "MOTOR_PRODUCAO";
            String executorLog = (fechamentoId != null && executadoPor != null && !executadoPor.isBlank()) ? executadoPor : "SISTEMA";
            logsParaSalvar.add(criarLogImutavel(resultado, sale, resolucao, origemLog, executorLog));

            sucessos.add(toItem(resultado));
            totalVendasCalculadas = totalVendasCalculadas.add(resultado.getValorVenda());
            totalComissoesCalculadas = totalComissoesCalculadas.add(resultado.getValorComissao());
        }

        // Persistência em blocos
        salvarEmBlocos(resultadosParaSalvar, logsParaSalvar);

        return new CalculoCompetenciaResponseDTO(
                competenciaStr,
                vendas.size(),
                sucessos.size(),
                impedimentos.size(),
                totalVendasCalculadas,
                totalComissoesCalculadas,
                sucessos,
                impedimentos
        );
    }

    private Sale bloquearVenda(Sale sale) {
        if (sale == null || sale.getId() == null || saleRepository == null) {
            return sale;
        }
        return saleRepository.findByIdForUpdate(sale.getId()).orElse(sale);
    }

    private CalculoImpedimentoDTO impedimentoPorDivergencia(
            UUID idVenda,
            String matricula,
            Sale sale,
            ResultadoCalculo existente
    ) {
        return new CalculoImpedimentoDTO(
                idVenda,
                matricula,
                sale.getSaleDate(),
                sale.getValue(),
                String.format("Venda com ID '%s' possui dados divergentes de cálculo prévio (anterior: %s, atual: %s)",
                        idVenda, existente.getValorVenda(), sale.getValue())
        );
    }

    private ResultadoCalculo atualizarResultadoExistente(
            ResultadoCalculo resultado,
            UUID idVenda,
            String matricula,
            Integer codMarca,
            Integer codLoja,
            Integer codCargo,
            Long idRegra,
            Sale sale,
            BigDecimal taxaAplicada,
            BigDecimal comissao,
            String origemTaxa
    ) {
        resultado.setProtocoloCalculo(UUID.randomUUID());
        resultado.setIdVenda(idVenda);
        resultado.setMatricula(matricula);
        resultado.setCodMarca(codMarca);
        resultado.setCodLoja(codLoja);
        resultado.setCodCargo(codCargo);
        resultado.setRegraId(idRegra);
        resultado.setDataVenda(sale.getSaleDate());
        resultado.setValorVenda(sale.getValue());
        resultado.setTaxaAplicada(taxaAplicada);
        resultado.setValorComissao(comissao);
        resultado.setOrigemTaxa(origemTaxa);
        resultado.setTipoCalculo("COMPETENCIA");
        resultado.setCalculadoEm(OffsetDateTime.now());
        return resultado;
    }

    private CalculoItemResponseDTO toItem(ResultadoCalculo resultado) {
        return new CalculoItemResponseDTO(
                resultado.getProtocoloCalculo(),
                resultado.getIdVenda(),
                resultado.getMatricula(),
                resultado.getCodCargo(),
                resultado.getCodMarca(),
                resultado.getCodLoja(),
                resultado.getDataVenda(),
                resultado.getValorVenda(),
                resultado.getTaxaAplicada(),
                resultado.getValorComissao(),
                resultado.getRegraId(),
                resultado.getOrigemTaxa(),
                resultado.getCalculadoEm(),
                resultado.getVersaoCampanhaNumero()
        );
    }

    private LogCalculoImutavel criarLogImutavel(ResultadoCalculo resultado, Sale sale, ResolucaoTaxaResult resolucao) {
        return criarLogImutavel(resultado, sale, resolucao, "MOTOR_PRODUCAO", "SISTEMA");
    }

    private LogCalculoImutavel criarLogImutavel(
            ResultadoCalculo resultado,
            Sale sale,
            ResolucaoTaxaResult resolucao,
            String origemExecucao,
            String usuarioExecutor
    ) {
        String tipoVenda = sale.getTipoVenda() != null && !sale.getTipoVenda().isBlank()
                ? sale.getTipoVenda() : "IMPORTADA";
        String canal = sale.getSaleChannel() != null ? sale.getSaleChannel() : "PADRAO";
        String versaoRegra = resolucao.versaoOuReferenciaRegra() != null
                ? resolucao.versaoOuReferenciaRegra() : "REGRA#" + resultado.getRegraId();
        ComissaoCalculada calculada = new ComissaoCalculada(
                resultado.getValorComissao(),
                resultado.getValorBonusPercentual(),
                resultado.getValorBonusFixo()
        );
        String parametrosSnapshot = montarSnapshotParametros(
                resultado.getValorVenda(), resultado.getTaxaAplicada(), resultado.getValorComissao(),
                resultado.getRegraId(), resolucao.origemTaxa(), versaoRegra, resultado.getDataVenda(),
                resultado.getMatricula(), resultado.getCodCargo(), resultado.getCodLoja(), resultado.getCodMarca(),
                canal, tipoVenda, sale.getIdLoteOrigem(),
                calculada
        );
        LogCalculoImutavel logImutavel = new LogCalculoImutavel(
                resultado.getProtocoloCalculo(),
                resultado.getIdVenda(),
                resultado.getMatricula(),
                resultado.getCodCargo(),
                resultado.getCodLoja(),
                resultado.getCodMarca(),
                resultado.getValorVenda(),
                resultado.getTaxaAplicada(),
                resultado.getValorComissao(),
                resultado.getRegraId(),
                resultado.getDataVenda(),
                canal,
                origemExecucao != null ? origemExecucao : "MOTOR_PRODUCAO",
                tipoVenda,
                sale.getIdLoteOrigem(),
                versaoRegra,
                parametrosSnapshot
        );
        logImutavel.setOrigemTaxa(resolucao.origemTaxa());
        logImutavel.setValorBonusPercentual(resultado.getValorBonusPercentual());
        logImutavel.setValorBonusFixo(resultado.getValorBonusFixo());
        if (usuarioExecutor != null && !usuarioExecutor.isBlank()) {
            logImutavel.setUsuarioExecutor(usuarioExecutor);
        }
        return logImutavel;
    }

    private void registrarLog(ResultadoCalculo resultado, Sale sale, ResolucaoTaxaResult resolucao) {
        logCalculoRepository.save(criarLogImutavel(resultado, sale, resolucao));
    }

    private static final int TAMANHO_BLOCO = 100;

    private void salvarEmBlocos(List<ResultadoCalculo> resultados, List<LogCalculoImutavel> logs) {
        if (resultados != null && !resultados.isEmpty()) {
            if (resultados.size() == 1) {
                resultadoCalculoRepository.save(resultados.get(0));
            } else {
                for (int i = 0; i < resultados.size(); i += TAMANHO_BLOCO) {
                    int fimBloco = Math.min(i + TAMANHO_BLOCO, resultados.size());
                    resultadoCalculoRepository.saveAll(resultados.subList(i, fimBloco));
                    if (entityManager != null) {
                        entityManager.flush();
                    }
                }
            }
        }

        if (logs != null && !logs.isEmpty()) {
            if (logs.size() == 1) {
                logCalculoRepository.save(logs.get(0));
            } else {
                for (int i = 0; i < logs.size(); i += TAMANHO_BLOCO) {
                    int fimBloco = Math.min(i + TAMANHO_BLOCO, logs.size());
                    logCalculoRepository.saveAll(logs.subList(i, fimBloco));
                    if (entityManager != null) {
                        entityManager.flush();
                        entityManager.clear();
                    }
                }
            }
        }
    }

    /**
     * Comissão total = valor × taxa + valor × bônus percentual + bônus de valor fixo (por venda elegível).
     * Cada parcela é arredondada separadamente em escala 2 com HALF_UP; o total é a soma das parcelas arredondadas.
     */
    private ComissaoCalculada calcularComissaoTotal(BigDecimal valorVenda, BigDecimal taxa, ResolucaoTaxaResult resolucao) {
        BigDecimal base = valorVenda.multiply(taxa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bonusPercentual = resolucao.bonusPercentual() != null
                ? valorVenda.multiply(resolucao.bonusPercentual()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal bonusFixo = resolucao.bonusValorFixo() != null
                ? resolucao.bonusValorFixo().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new ComissaoCalculada(base.add(bonusPercentual).add(bonusFixo), bonusPercentual, bonusFixo);
    }

    private record ComissaoCalculada(BigDecimal total, BigDecimal bonusPercentual, BigDecimal bonusFixo) {}

    private void validarNovoCalculoPermitido(LocalDate data) {
        if (data == null || fechamentoCompetenciaRepository == null) return;
        String competencia = YearMonth.from(data).toString();
        if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, com.bugbusters.backend.model.StatusFechamento.CONCLUIDO))
            throw new BusinessException("A competência da venda (" + competencia + ") possui fechamento final concluído e está bloqueada para novas apurações.");
        if (fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, com.bugbusters.backend.model.StatusFechamento.PROCESSANDO))
            throw new BusinessException("A competência da venda (" + competencia + ") está em processo de fechamento final e não permite novas apurações.");
    }

    /**
     * Processa o cálculo de comissão garantindo proteção contra cálculo duplicado (idempotência).
     * Mantido para compatibilidade integral com chamadas avulsas e contratos legados.
     */
    @Transactional
    public CalculoComissaoResponse calcularComissao(CalculoComissaoRequest request) {
        Sale persistida = request.idVenda() != null && saleRepository != null ? saleRepository.findById(request.idVenda()).orElse(null) : null;
        var competenciasAfetadas = new java.util.TreeSet<String>();
        if (request.dataVenda() != null) competenciasAfetadas.add(YearMonth.from(request.dataVenda()).toString());
        if (persistida != null && persistida.getSaleDate() != null) competenciasAfetadas.add(YearMonth.from(persistida.getSaleDate()).toString());
        if (travaCompetencia != null) for (String competencia : competenciasAfetadas) travaCompetencia.adquirirTrava(competencia);
        // 1. Verifica se já existe cálculo pelo UUID da venda (se informado) ou pela chave de negócio
        Optional<ResultadoCalculo> existenteOpt = Optional.empty();
        if (request.idVenda() != null && resultadoCalculoRepository != null) {
            existenteOpt = resultadoCalculoRepository.findByIdVenda(request.idVenda());
        }

        if (existenteOpt.isPresent()) {
            ResultadoCalculo existente = existenteOpt.get();
            return tratarResultadoExistente(request, existente);
        }

        validarNovoCalculoPermitido(request.dataVenda());
        if (persistida != null) validarNovoCalculoPermitido(persistida.getSaleDate());

        // Validação de cobertura ANTES de qualquer escrita em tb_resultado_calculo ou log imutável
        if (coberturaBaseService != null) {
            coberturaBaseService.validarCoberturaAvulsa(
                    request.codMarca(), request.descrMarca(), request.matricula(), request.dataVenda()
            );
        }

        // 2. Localiza venda persistida ou constrói venda com vínculos necessários (BACK-14)
        Sale sale = persistida;
        if (sale == null) {
            sale = new Sale();
            sale.setId(request.idVenda());
            sale.setValue(request.valorVenda());
            sale.setSaleDate(request.dataVenda());
            sale.setSaleChannel(request.canal());
            sale.setTipoVenda("INFORMADA");

            Registration reg = null;
            if (registrationRepository != null && request.matricula() != null) {
                reg = registrationRepository.findByRegistration(request.matricula()).orElse(null);
            }
            if (reg == null && request.matricula() != null) {
                reg = new Registration();
                reg.setRegistration(request.matricula());
            }
            sale.setRegistration(reg);

            Brand brand = null;
            if (brandRepository != null) {
                if (request.codMarca() != null) {
                    brand = brandRepository.findByCode(request.codMarca()).orElse(null);
                }
                if (brand == null && request.descrMarca() != null) {
                    brand = brandRepository.findByDescription(request.descrMarca()).orElse(null);
                }
            }
            if (brand == null && request.codMarca() != null) {
                brand = new Brand();
                brand.setCode(request.codMarca());
                if (request.descrMarca() != null) {
                    brand.setDescription(request.descrMarca());
                }
            }
            sale.setBrand(brand);

            Store store = null;
            if (storeRepository != null && request.codLoja() != null) {
                store = storeRepository.findByCode(request.codLoja()).orElse(null);
            }
            if (store == null && reg != null && reg.getStore() != null) {
                store = reg.getStore();
            }
            if (store == null && request.codLoja() != null) {
                store = new Store();
                store.setCode(request.codLoja());
            }
            sale.setStore(store);
        } else {
            if (request.valorVenda() != null) {
                sale.setValue(request.valorVenda());
            }
            if (request.dataVenda() != null) {
                sale.setSaleDate(request.dataVenda());
            }
            if (request.canal() != null) {
                sale.setSaleChannel(request.canal());
            }
        }

        // 3. Resolve a taxa com o motor compartilhado (S1-B08 / BACK-14)
        ResolucaoTaxaResult resolucao = null;
        if (taxaComissaoResolver != null) {
            resolucao = taxaComissaoResolver.resolverTaxa(sale);
        }
        if (resolucao == null) {
            resolucao = ResolucaoTaxaResult.sucesso(TAXA_PADRAO, REGRA_PADRAO_ID, "PADRAO");
        }

        if (!resolucao.sucesso()) {
            throw new BusinessException(resolucao.motivoImpedimento());
        }

        BigDecimal taxaAplicada = resolucao.taxa();
        Long idRegra = resolucao.idRegra() != null ? resolucao.idRegra() : REGRA_PADRAO_ID;
        garantirRegraPadraoExistente(idRegra, taxaAplicada);

        ComissaoCalculada calculada = calcularComissaoTotal(request.valorVenda(), taxaAplicada, resolucao);
        BigDecimal comissao = calculada.total();
        UUID protocolo = UUID.randomUUID();

        if (travaCompetencia != null && request.dataVenda() != null) {
            travaCompetencia.adquirirTrava(java.time.YearMonth.from(request.dataVenda()).toString());
        }

        Integer codCargo = sale.getRegistration() != null && sale.getRegistration().getPosition() != null
                ? sale.getRegistration().getPosition().getCode() : null;
        Integer codMarca = sale.getBrand() != null ? sale.getBrand().getCode() : request.codMarca();
        Integer codLoja = sale.getStore() != null ? sale.getStore().getCode() : request.codLoja();

        ResultadoCalculo novoResultado = new ResultadoCalculo(
                protocolo,
                request.idVenda(),
                request.matricula(),
                codMarca,
                codLoja,
                codCargo,
                idRegra,
                request.dataVenda(),
                request.valorVenda(),
                taxaAplicada,
                comissao,
                "INDIVIDUAL"
        );
        novoResultado.setOrigemTaxa(resolucao.origemTaxa());
        novoResultado.setValorBonusPercentual(calculada.bonusPercentual());
        novoResultado.setValorBonusFixo(calculada.bonusFixo());

        try {
            ResultadoCalculo resultadoSalvo = resultadoCalculoRepository.save(novoResultado);

            // Grava o log imutável de auditoria apenas na primeira execução com sucesso (BUG-23)
            String tipoVenda = sale.getTipoVenda() != null && !sale.getTipoVenda().isBlank()
                    ? sale.getTipoVenda() : "INFORMADA";
            String idLoteOrigem = sale.getIdLoteOrigem();
            String versaoRegra = resolucao.versaoOuReferenciaRegra() != null
                    ? resolucao.versaoOuReferenciaRegra()
                    : "REGRA#" + idRegra;
            String parametrosSnapshot = montarSnapshotParametros(
                    request.valorVenda(), taxaAplicada, comissao, idRegra, resolucao.origemTaxa(), versaoRegra,
                    request.dataVenda(), request.matricula(), codCargo, codLoja, codMarca,
                    request.canal(), tipoVenda, idLoteOrigem, calculada
            );

            LogCalculoImutavel logImutavel = new LogCalculoImutavel(
                    resultadoSalvo.getProtocoloCalculo(),
                    request.idVenda(),
                    request.matricula(),
                    codCargo,
                    codLoja,
                    codMarca,
                    request.valorVenda(),
                    taxaAplicada,
                    comissao,
                    idRegra,
                    request.dataVenda(),
                    request.canal(),
                    "MOTOR_PRODUCAO",
                    tipoVenda,
                    idLoteOrigem,
                    versaoRegra,
                    parametrosSnapshot
            );
            logImutavel.setOrigemTaxa(resolucao.origemTaxa());
            logImutavel.setValorBonusPercentual(calculada.bonusPercentual());
            logImutavel.setValorBonusFixo(calculada.bonusFixo());
            logCalculoRepository.save(logImutavel);

            return new CalculoComissaoResponse(
                    resultadoSalvo.getProtocoloCalculo(),
                    resultadoSalvo.getMatricula(),
                    resultadoSalvo.getRegraId(),
                    resultadoSalvo.getTaxaAplicada(),
                    resultadoSalvo.getValorVenda(),
                    resultadoSalvo.getValorComissao(),
                    resultadoSalvo.getCalculadoEm()
            );
        } catch (DataIntegrityViolationException ex) {
            log.warn("Violação de integridade por concorrência detectada ao persistir cálculo para matrícula {} na data {}. Recuperando resultado existente.",
                    request.matricula(), request.dataVenda());

            ResultadoCalculo resultadoConcorrente = null;
            if (request.idVenda() != null) {
                resultadoConcorrente = resultadoCalculoRepository.findByIdVenda(request.idVenda()).orElse(null);
            }
            if (resultadoConcorrente == null) {
                resultadoConcorrente = resultadoCalculoRepository
                        .findByMatriculaAndDataVendaAndRegraId(request.matricula(), request.dataVenda(), idRegra)
                        .orElseThrow(() -> ex);
            }

            return tratarResultadoExistente(request, resultadoConcorrente);
        }
    }

    private CalculoComissaoResponse tratarResultadoExistente(CalculoComissaoRequest request, ResultadoCalculo existente) {
        if (request.valorVenda().compareTo(existente.getValorVenda()) != 0) {
            String identificador = request.idVenda() != null
                    ? "ID '" + request.idVenda() + "'"
                    : String.format("matrícula '%s' na data '%s'", request.matricula(), request.dataVenda());
            throw new BusinessException(String.format(
                    "Solicitação rejeitada por duplicidade com dados divergentes. A venda para %s já foi calculada com valor %s (valor recebido: %s).",
                    identificador, existente.getValorVenda(), request.valorVenda()
            ));
        }

        log.info("Idempotência aplicada: retornando cálculo já existente (protocolo: {}) sem gerar novo log.",
                existente.getProtocoloCalculo());

        return new CalculoComissaoResponse(
                existente.getProtocoloCalculo(),
                existente.getMatricula(),
                existente.getRegraId(),
                existente.getTaxaAplicada(),
                existente.getValorVenda(),
                existente.getValorComissao(),
                existente.getCalculadoEm()
        );
    }

    /**
     * Consulta paginada dos logs imutáveis de cálculos com filtros combináveis.
     * Retorna valores históricos preservados sem recalcular nada durante a consulta.
     *
     * @param idVenda identificador único da venda (opcional)
     * @param matricula matrícula do colaborador (opcional)
     * @param idRegra identificador da regra aplicada (opcional)
     * @param dataInicio início do período de venda (opcional)
     * @param dataFim fim do período de venda (opcional)
     * @param pageable parâmetros de paginação e ordenação
     * @return página de logs de auditoria
     */
    @Transactional(readOnly = true)
    public Page<LogCalculoResponse> listarLogs(
            UUID idVenda,
            String matricula,
            Long idRegra,
            LocalDate dataInicio,
            LocalDate dataFim,
            String tipoVenda,
            String idLoteOrigem,
            Pageable pageable
    ) {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new BusinessException("Data de início não pode ser posterior à data de término.");
        }

        Specification<LogCalculoImutavel> spec = LogCalculoSpecification.comFiltros(
                idVenda, matricula, idRegra, dataInicio, dataFim, tipoVenda, idLoteOrigem
        );

        Page<LogCalculoImutavel> page = logCalculoRepository.findAll(spec, pageable);
        return page.map(LogCalculoResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<LogCalculoResponse> listarLogs(
            UUID idVenda,
            String matricula,
            Long idRegra,
            LocalDate dataInicio,
            LocalDate dataFim,
            Pageable pageable
    ) {
        return listarLogs(idVenda, matricula, idRegra, dataInicio, dataFim, null, null, pageable);
    }

    /**
     * Consulta os detalhes de um log de cálculo específico por seu identificador único.
     * Preserva referências e valores históricos sem recalcular.
     *
     * @param id identificador único do log
     * @return detalhes completos do log de cálculo
     * @throws ResourceNotFoundException caso o log não exista
     */
    @Transactional(readOnly = true)
    public LogCalculoResponse buscarPorId(UUID id) {
        LogCalculoImutavel log = logCalculoRepository.findById(id)
                .or(() -> logCalculoRepository.findByProtocolo(id).stream().findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("Log de cálculo não encontrado com o identificador ou protocolo: " + id));
        return LogCalculoResponse.fromEntity(log);
    }

    /**
     * Consulta todos os logs imutáveis de cálculos gerados no sistema (sem paginação).
     * Retorna lista vazia legítima caso não existam registros.
     */
    @Transactional(readOnly = true)
    public List<LogCalculoResponse> listarLogs() {
        return logCalculoRepository.findAllByOrderByExecutadoEmDesc()
                .stream()
                .map(LogCalculoResponse::fromEntity)
                .toList();
    }

    private String montarSnapshotParametros(
            BigDecimal valorVenda, BigDecimal taxaAplicada, BigDecimal valorComissao,
            Long idRegra, String origemTaxa, String versaoRegra,
            LocalDate dataVenda, String matricula, Integer codCargo, Integer codLoja, Integer codMarca,
            String canal, String tipoVenda, String idLoteOrigem, ComissaoCalculada calculada
    ) {
        String base = montarSnapshotParametros(valorVenda, taxaAplicada, valorComissao, idRegra, origemTaxa, versaoRegra,
                dataVenda, matricula, codCargo, codLoja, codMarca, canal, tipoVenda, idLoteOrigem);
        if (calculada == null || (calculada.bonusPercentual().signum() == 0 && calculada.bonusFixo().signum() == 0)) {
            return base;
        }
        return base.substring(0, base.length() - 1)
                + ",\"valorBonusPercentual\":" + calculada.bonusPercentual()
                + ",\"valorBonusFixo\":" + calculada.bonusFixo() + "}";
    }

    private String montarSnapshotParametros(
            BigDecimal valorVenda, BigDecimal taxaAplicada, BigDecimal valorComissao,
            Long idRegra, String origemTaxa, String versaoRegra,
            LocalDate dataVenda, String matricula, Integer codCargo, Integer codLoja, Integer codMarca,
            String canal, String tipoVenda, String idLoteOrigem
    ) {
        return String.format(
                "{\"valorVenda\":%s,\"taxaAplicada\":%s,\"valorComissao\":%s,\"idRegra\":%s," +
                "\"origemTaxa\":\"%s\",\"versaoRegra\":\"%s\",\"dataVenda\":\"%s\",\"matricula\":\"%s\"," +
                "\"codCargo\":%s,\"codLoja\":%s,\"codMarca\":%s,\"canal\":\"%s\",\"tipoVenda\":\"%s\",\"idLoteOrigem\":%s}",
                valorVenda != null ? valorVenda : BigDecimal.ZERO,
                taxaAplicada != null ? taxaAplicada : BigDecimal.ZERO,
                valorComissao != null ? valorComissao : BigDecimal.ZERO,
                idRegra != null ? idRegra : 1L,
                origemTaxa != null ? origemTaxa : "PADRAO",
                versaoRegra != null ? versaoRegra : "",
                dataVenda != null ? dataVenda : "",
                matricula != null ? matricula : "",
                codCargo != null ? codCargo.toString() : "null",
                codLoja != null ? codLoja.toString() : "null",
                codMarca != null ? codMarca.toString() : "null",
                canal != null ? canal : "",
                tipoVenda != null ? tipoVenda : "INFORMADA",
                idLoteOrigem != null ? "\"" + idLoteOrigem + "\"" : "null"
        );
    }

    private Regra garantirRegraPadraoExistente(Long idRegra, BigDecimal taxa) {
        if (regraRepository == null) {
            return null;
        }
        if (regraRepository.existsById(idRegra)) {
            return regraRepository.findById(idRegra).orElse(null);
        }

        Regra defaultRegra = new Regra(
                null,
                "Regra Geral Padrão",
                "PADRAO",
                taxa,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2035, 12, 31)
        );
        return regraRepository.save(defaultRegra);
    }
}
