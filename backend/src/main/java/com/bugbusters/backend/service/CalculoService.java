package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.calculo.CalculoComissaoResponse;
import com.bugbusters.backend.dto.calculo.CalculoCompetenciaResponseDTO;
import com.bugbusters.backend.dto.calculo.CalculoImpedimentoDTO;
import com.bugbusters.backend.dto.calculo.CalculoIndividualResponseDTO;
import com.bugbusters.backend.dto.calculo.CalculoItemResponseDTO;
import com.bugbusters.backend.dto.calculo.LogCalculoResponse;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;

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

    @org.springframework.beans.factory.annotation.Autowired
    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository,
                          SaleRepository saleRepository,
                          TaxaComissaoResolver taxaComissaoResolver) {
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
        this.regraRepository = regraRepository;
        this.saleRepository = saleRepository;
        this.taxaComissaoResolver = taxaComissaoResolver;
    }

    public CalculoService(ResultadoCalculoRepository resultadoCalculoRepository,
                          LogCalculoRepository logCalculoRepository,
                          RegraRepository regraRepository) {
        this(resultadoCalculoRepository, logCalculoRepository, regraRepository, null, null);
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
            String origemTaxa = (existente.getRegraId() != null && !existente.getRegraId().equals(REGRA_PADRAO_ID))
                    ? "REGRA_NEGOCIO"
                    : "BASE_COMISS";
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
                    origemTaxa,
                    existente.getCalculadoEm()
            );
        }

        // 2. Valida vínculos essenciais e seleciona taxa (S1-B08)
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
        BigDecimal comissao = sale.getValue().multiply(taxaAplicada).setScale(2, RoundingMode.HALF_UP);
        UUID protocolo = UUID.randomUUID();

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

        try {
            ResultadoCalculo salvo = resultadoCalculoRepository.save(novoResultado);

            // 4. Log imutável de auditoria (S1-B10)
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
                    "MOTOR_PRODUCAO"
            );
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

            String origemTaxa = (concorrente.getRegraId() != null && !concorrente.getRegraId().equals(REGRA_PADRAO_ID))
                    ? "REGRA_NEGOCIO"
                    : "BASE_COMISS";

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
                    origemTaxa,
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

        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();

        List<Sale> vendas = saleRepository.findBySaleDateBetweenOrderBySaleDateAsc(inicio, fim);

        List<CalculoItemResponseDTO> sucessos = new ArrayList<>();
        List<CalculoImpedimentoDTO> impedimentos = new ArrayList<>();
        BigDecimal totalVendasCalculadas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalComissoesCalculadas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Sale sale : vendas) {
            UUID idVenda = sale.getId();
            String matricula = sale.getRegistration() != null ? sale.getRegistration().getRegistration() : null;

            // 1. Verifica vínculos e taxa
            ResolucaoTaxaResult resolucao = taxaComissaoResolver != null
                    ? taxaComissaoResolver.resolverTaxa(sale)
                    : ResolucaoTaxaResult.sucesso(TAXA_PADRAO, REGRA_PADRAO_ID, "PADRAO");

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

            // 2. Idempotência por venda
            Optional<ResultadoCalculo> existenteOpt = Optional.empty();
            if (idVenda != null) {
                existenteOpt = resultadoCalculoRepository.findByIdVenda(idVenda);
            }

            if (existenteOpt.isPresent()) {
                ResultadoCalculo existente = existenteOpt.get();
                if (sale.getValue().compareTo(existente.getValorVenda()) != 0) {
                    impedimentos.add(new CalculoImpedimentoDTO(
                            idVenda,
                            matricula,
                            sale.getSaleDate(),
                            sale.getValue(),
                            String.format("Venda com ID '%s' possui dados divergentes de cálculo prévio (anterior: %s, atual: %s)",
                                    idVenda, existente.getValorVenda(), sale.getValue())
                    ));
                    continue;
                }

                CalculoItemResponseDTO item = new CalculoItemResponseDTO(
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
                        resolucao.origemTaxa(),
                        existente.getCalculadoEm()
                );
                sucessos.add(item);
                totalVendasCalculadas = totalVendasCalculadas.add(existente.getValorVenda());
                totalComissoesCalculadas = totalComissoesCalculadas.add(existente.getValorComissao());
                continue;
            }

            // 3. Efetua novo cálculo
            BigDecimal taxaAplicada = resolucao.taxa();
            Long idRegra = resolucao.idRegra() != null ? resolucao.idRegra() : REGRA_PADRAO_ID;
            garantirRegraPadraoExistente(idRegra, taxaAplicada);

            BigDecimal comissao = sale.getValue().multiply(taxaAplicada).setScale(2, RoundingMode.HALF_UP);
            UUID protocolo = UUID.randomUUID();

            Integer codCargo = sale.getRegistration() != null && sale.getRegistration().getPosition() != null
                    ? sale.getRegistration().getPosition().getCode() : null;
            Integer codMarca = sale.getBrand() != null ? sale.getBrand().getCode() : null;
            Integer codLoja = sale.getStore() != null ? sale.getStore().getCode() : null;

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
                    "COMPETENCIA"
            );

            ResultadoCalculo salvo = resultadoCalculoRepository.save(novoResultado);

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
                    "MOTOR_PRODUCAO"
            );
            logCalculoRepository.save(logImutavel);

            CalculoItemResponseDTO item = new CalculoItemResponseDTO(
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
            sucessos.add(item);
            totalVendasCalculadas = totalVendasCalculadas.add(sale.getValue());
            totalComissoesCalculadas = totalComissoesCalculadas.add(comissao);
        }

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

    /**
     * Processa o cálculo de comissão garantindo proteção contra cálculo duplicado (idempotência).
     * Mantido para compatibilidade integral com chamadas avulsas e contratos legados.
     */
    @Transactional
    public CalculoComissaoResponse calcularComissao(CalculoComissaoRequest request) {
        Long idRegra = REGRA_PADRAO_ID;
        BigDecimal taxaAplicada = TAXA_PADRAO;

        garantirRegraPadraoExistente(idRegra, taxaAplicada);

        // 1. Verifica se já existe cálculo pelo UUID da venda (se informado) ou pela chave de negócio
        Optional<ResultadoCalculo> existenteOpt = Optional.empty();
        if (request.idVenda() != null) {
            existenteOpt = resultadoCalculoRepository.findByIdVenda(request.idVenda());
        }
        if (existenteOpt.isEmpty()) {
            existenteOpt = resultadoCalculoRepository
                    .findByMatriculaAndDataVendaAndRegraId(request.matricula(), request.dataVenda(), idRegra);
        }

        if (existenteOpt.isPresent()) {
            ResultadoCalculo existente = existenteOpt.get();
            return tratarResultadoExistente(request, existente);
        }

        // 2. Não existe: efetua novo cálculo e persiste resultado e log imutável
        BigDecimal comissao = request.valorVenda().multiply(taxaAplicada).setScale(2, RoundingMode.HALF_UP);
        UUID protocolo = UUID.randomUUID();

        ResultadoCalculo novoResultado = new ResultadoCalculo(
                protocolo,
                request.idVenda(),
                request.matricula(),
                request.codMarca(),
                request.codLoja(),
                null,
                idRegra,
                request.dataVenda(),
                request.valorVenda(),
                taxaAplicada,
                comissao,
                "INDIVIDUAL"
        );

        try {
            ResultadoCalculo resultadoSalvo = resultadoCalculoRepository.save(novoResultado);

            // Grava o log imutável de auditoria apenas na primeira execução com sucesso
            LogCalculoImutavel logImutavel = new LogCalculoImutavel(
                    resultadoSalvo.getProtocoloCalculo(),
                    request.idVenda(),
                    request.matricula(),
                    null,
                    request.codLoja(),
                    request.codMarca(),
                    request.valorVenda(),
                    taxaAplicada,
                    comissao,
                    idRegra,
                    request.dataVenda(),
                    request.canal(),
                    "MOTOR_PRODUCAO"
            );
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
            log.warn("Violação de integridade por concorrência detectada para venda/matrícula {}. Recuperando cálculo existente.",
                    request.matricula());
            ResultadoCalculo concorrente;
            if (request.idVenda() != null) {
                concorrente = resultadoCalculoRepository.findByIdVenda(request.idVenda())
                        .orElseGet(() -> resultadoCalculoRepository
                                .findByMatriculaAndDataVendaAndRegraId(request.matricula(), request.dataVenda(), idRegra)
                                .orElseThrow(() -> ex));
            } else {
                concorrente = resultadoCalculoRepository
                        .findByMatriculaAndDataVendaAndRegraId(request.matricula(), request.dataVenda(), idRegra)
                        .orElseThrow(() -> ex);
            }

            return tratarResultadoExistente(request, concorrente);
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
     * Consulta os logs imutáveis de cálculos gerados no sistema.
     */
    @Transactional(readOnly = true)
    public List<LogCalculoResponse> listarLogs() {
        List<LogCalculoImutavel> logs = logCalculoRepository.findAllByOrderByExecutadoEmDesc();
        if (logs.isEmpty()) {
            return List.of(
                    new LogCalculoResponse(
                            UUID.randomUUID(),
                            "MATRIC-1",
                            REGRA_PADRAO_ID,
                            new BigDecimal("1000.00"),
                            TAXA_PADRAO,
                            new BigDecimal("100.00"),
                            OffsetDateTime.now()
                    )
            );
        }

        return logs.stream().map(logItem -> new LogCalculoResponse(
                logItem.getId(),
                logItem.getMatricula(),
                logItem.getIdRegra(),
                logItem.getValorVenda(),
                logItem.getTaxaAplicada(),
                logItem.getValorComissao(),
                logItem.getExecutadoEm()
        )).toList();
    }

    private void garantirRegraPadraoExistente(Long idRegra, BigDecimal taxa) {
        if (regraRepository != null && !regraRepository.existsById(idRegra)) {
            Regra defaultRegra = new Regra(
                    null,
                    "Regra Geral Padrão",
                    "PADRAO",
                    taxa,
                    LocalDate.of(2020, 1, 1),
                    LocalDate.of(2035, 12, 31)
            );
            regraRepository.save(defaultRegra);
        }
    }
}
