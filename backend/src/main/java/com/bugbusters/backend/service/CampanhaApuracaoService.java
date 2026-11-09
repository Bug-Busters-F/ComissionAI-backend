package com.bugbusters.backend.service;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.apuracao.*;
import com.bugbusters.backend.dto.calculo.CalculoImpedimentoDTO;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.pendencia.ResumoPendenciasCampanhaResponse;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço de Apuração Definitiva por Campanha no Motor Atual (BUG-109 / BACK-14).
 * <p>
 * Executa a liquidação e o cálculo definitivo das comissões sobre as vendas
 * abrangidas pela campanha, respeitando o programa Python homologado, a esteira
 * de aprovação, o aceite de pendências impeditivas e registrando os resultados
 * congelados em tb_resultado_calculo e os logs de auditoria imutável.
 * </p>
 */
@Service
public class CampanhaApuracaoService {

    private static final Logger log = LoggerFactory.getLogger(CampanhaApuracaoService.class);
    private static final int TAMANHO_BLOCO = 100;

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;
    private final CampanhaProgramaPythonRepository programaPythonRepository;
    private final CampanhaPendenciaService pendenciaService;
    private final CampanhaApuracaoRepository apuracaoRepository;
    private final CampanhaEsteiraTransicaoRepository transicaoRepository;
    private final RegraRepository regraRepository;
    private final SaleRepository saleRepository;
    private final BaseComissRepository baseComissRepository;
    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;
    private final ReferenciaPublicoService referenciaPublicoService;
    private final FechamentoCompetenciaRepository fechamentoCompetenciaRepository;
    private final AuditoriaGeralService auditoriaService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CampanhaApuracaoService(
            CampanhaRepository campanhaRepository,
            CampanhaVersaoRepository campanhaVersaoRepository,
            CampanhaProgramaPythonRepository programaPythonRepository,
            @Autowired(required = false) CampanhaPendenciaService pendenciaService,
            CampanhaApuracaoRepository apuracaoRepository,
            @Autowired(required = false) CampanhaEsteiraTransicaoRepository transicaoRepository,
            RegraRepository regraRepository,
            SaleRepository saleRepository,
            BaseComissRepository baseComissRepository,
            ResultadoCalculoRepository resultadoCalculoRepository,
            LogCalculoRepository logCalculoRepository,
            ReferenciaPublicoService referenciaPublicoService,
            @Autowired(required = false) FechamentoCompetenciaRepository fechamentoCompetenciaRepository,
            @Autowired(required = false) AuditoriaGeralService auditoriaService
    ) {
        this.campanhaRepository = campanhaRepository;
        this.campanhaVersaoRepository = campanhaVersaoRepository;
        this.programaPythonRepository = programaPythonRepository;
        this.pendenciaService = pendenciaService;
        this.apuracaoRepository = apuracaoRepository;
        this.transicaoRepository = transicaoRepository;
        this.regraRepository = regraRepository;
        this.saleRepository = saleRepository;
        this.baseComissRepository = baseComissRepository;
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
        this.referenciaPublicoService = referenciaPublicoService;
        this.fechamentoCompetenciaRepository = fechamentoCompetenciaRepository;
        this.auditoriaService = auditoriaService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    /**
     * Executa a apuração definitiva e congela os resultados e logs para a campanha.
     */
    @Transactional
    public ApuracaoCampanhaResponse calcularDefinitivo(Long campanhaId, Integer versaoNumero, CalculoDefinitivoCampanhaRequest request) {
        if (request == null) {
            request = new CalculoDefinitivoCampanhaRequest(null, null, null, "SISTEMA", null, false);
        }

        // 1. Resolução da Campanha e Versão
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(campanhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha não encontrada com ID: " + campanhaId));

        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);

        // 2. Validação da Esteira (não pode estar DEVOLVIDA)
        if (versao.getEstadoEsteira() == EstadoEsteiraCampanha.DEVOLVIDA) {
            throw new BusinessException("A campanha está devolvida e não pode ser executada para apuração definitiva. Ajuste a proposta antes de calcular.");
        }

        // 3. Validação do Programa Python homologado
        CampanhaProgramaPython programa = versao.getProgramaPythonAprovado();
        if (programa == null || !programa.isAprovado()) {
            if (programaPythonRepository != null) {
                programa = programaPythonRepository.findFirstByCampanhaVersaoIdAndStatusAprovacao(
                        versao.getId(), StatusAprovacaoPrograma.APROVADO
                ).orElse(null);
            }
        }

        if (programa == null || !programa.isAprovado()) {
            throw new BusinessException("A campanha não possui um programa Python homologado e aprovado tecnicamente para o cálculo definitivo.");
        }

        // 4. Validação de Pendências e Riscos (BUG-102)
        if (pendenciaService != null) {
            ResumoPendenciasCampanhaResponse resumoPendencias = pendenciaService.listarPendencias(campanhaId, versao.getNumero());
            if (resumoPendencias != null && !resumoPendencias.aptaParaAvanco()) {
                throw new BusinessException(String.format(
                        "A campanha possui %d pendência(s) impeditiva(s) em aberto que bloqueiam a apuração definitiva. Registre o aceite formal fundamentado antes de prosseguir.",
                        resumoPendencias.totalPendentesImpeditivas()
                ));
            }
        }

        // 5. Determinação da Competência e Período
        String competenciaStr = resolverCompetencia(request.competencia(), versao);
        validarTravaFechamento(competenciaStr, request.forcarRecalculo());

        LocalDate dataInicio = request.dataInicio() != null ? request.dataInicio() : versao.getDataInicio();
        LocalDate dataFim = request.dataFim() != null ? request.dataFim() : versao.getDataFim();

        if (dataInicio == null || dataFim == null) {
            YearMonth ym = YearMonth.parse(competenciaStr);
            if (dataInicio == null) dataInicio = ym.atDay(1);
            if (dataFim == null) dataFim = ym.atEndOfMonth();
        }

        if (dataFim.isBefore(dataInicio)) {
            throw new BusinessException("A data final da apuração não pode ser anterior à data inicial.");
        }

        // 6. Carregamento de vendas e regras da campanha
        List<Sale> vendasDoPeriodo = saleRepository.findVendasPorPeriodoComRelacionamentos(dataInicio, dataFim);
        List<BaseComiss> basesComissao = baseComissRepository != null
                ? baseComissRepository.findAllComRelacionamentos()
                : List.of();

        List<Regra> regrasEntidade = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
        if (regrasEntidade.isEmpty()) {
            regrasEntidade = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId());
        }
        if (regrasEntidade.isEmpty()) {
            throw new BusinessException("A campanha não possui regras cadastradas para a apuração definitiva.");
        }

        List<RegraItemRequest> regrasRequest = regrasEntidade.stream()
                .map(this::converterRegraParaItemRequest)
                .toList();

        // 7. Filtragem das vendas do público-alvo da campanha
        List<Sale> vendasCampanha = vendasDoPeriodo.stream()
                .filter(sale -> regrasRequest.stream().anyMatch(r -> referenciaPublicoService.atendePublico(r, sale)))
                .toList();

        // 8. Processamento do cálculo definitivo por venda
        List<CalculoImpedimentoDTO> impedimentos = new ArrayList<>();
        List<ResultadoCalculo> resultadosParaSalvar = new ArrayList<>();
        List<LogCalculoImutavel> logsParaSalvar = new ArrayList<>();

        BigDecimal totalVendas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalComissao = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBonus = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Otimização: pré-carrega resultados existentes por ID de venda
        List<UUID> idsVendas = vendasCampanha.stream()
                .map(Sale::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, ResultadoCalculo> existentesPorIdVenda = Collections.emptyMap();
        if (!idsVendas.isEmpty() && resultadoCalculoRepository != null) {
            existentesPorIdVenda = resultadoCalculoRepository.findByIdVendaIn(idsVendas).stream()
                    .collect(Collectors.toMap(ResultadoCalculo::getIdVenda, java.util.function.Function.identity(), (a, b) -> a));
        }

        for (Sale sale : vendasCampanha) {
            ItemProcessamentoVenda processamento = processarVenda(sale, regrasRequest, basesComissao);

            if (!processamento.valida()) {
                String matricula = sale.getRegistration() != null ? sale.getRegistration().getRegistration() : null;
                impedimentos.add(new CalculoImpedimentoDTO(
                        sale.getId(),
                        matricula,
                        sale.getSaleDate(),
                        sale.getValue(),
                        String.join("; ", processamento.impedimentos())
                ));
                continue;
            }

            ResultadoCalculo resultado = existentesPorIdVenda.get(sale.getId());
            if (resultado == null && resultadoCalculoRepository != null && sale.getId() != null) {
                resultado = resultadoCalculoRepository.findByIdVenda(sale.getId()).orElse(null);
            }

            Integer codCargo = sale.getRegistration() != null && sale.getRegistration().getPosition() != null
                    ? sale.getRegistration().getPosition().getCode() : null;
            Integer codMarca = sale.getBrand() != null ? sale.getBrand().getCode() : null;
            Integer codLoja = sale.getStore() != null ? sale.getStore().getCode() : null;
            String matricula = sale.getRegistration() != null ? sale.getRegistration().getRegistration() : null;

            if (resultado != null) {
                resultado.setProtocoloCalculo(UUID.randomUUID());
                resultado.setMatricula(matricula);
                resultado.setCodMarca(codMarca);
                resultado.setCodLoja(codLoja);
                resultado.setCodCargo(codCargo);
                resultado.setRegraId(processamento.regraId());
                resultado.setDataVenda(sale.getSaleDate());
                resultado.setValorVenda(sale.getValue());
                resultado.setTaxaAplicada(processamento.taxaFinal());
                resultado.setValorComissao(processamento.valorComissao());
                resultado.setValorBonusPercentual(processamento.parcelaBonusPerc());
                resultado.setValorBonusFixo(processamento.parcelaBonusFixo());
                resultado.setTipoCalculo("CAMPANHA_DEFINITIVO");
                resultado.setOrigemTaxa(processamento.origemTaxa());
                resultado.setCalculadoEm(OffsetDateTime.now());
                resultado.setCampanhaId(campanha.getId());
                resultado.setVersaoCampanhaNumero(versao.getNumero());
                resultado.setVersaoProgramaPython(programa.getVersaoPrograma());
                resultado.setChecksumPython(programa.getChecksumSha256());
            } else {
                resultado = new ResultadoCalculo(
                        UUID.randomUUID(),
                        sale.getId(),
                        matricula,
                        codMarca,
                        codLoja,
                        codCargo,
                        processamento.regraId(),
                        sale.getSaleDate(),
                        sale.getValue(),
                        processamento.taxaFinal(),
                        processamento.valorComissao(),
                        "CAMPANHA_DEFINITIVO"
                );
                resultado.setValorBonusPercentual(processamento.parcelaBonusPerc());
                resultado.setValorBonusFixo(processamento.parcelaBonusFixo());
                resultado.setOrigemTaxa(processamento.origemTaxa());
                resultado.setCampanhaId(campanha.getId());
                resultado.setVersaoCampanhaNumero(versao.getNumero());
                resultado.setVersaoProgramaPython(programa.getVersaoPrograma());
                resultado.setChecksumPython(programa.getChecksumSha256());
            }

            resultadosParaSalvar.add(resultado);

            // Log de auditoria imutável (BUG-23)
            String snapshotParametros = montarSnapshot(resultado, processamento, versao, programa);
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
                    resultado.getRegraId() != null ? resultado.getRegraId() : 0L,
                    resultado.getDataVenda(),
                    sale.getSaleChannel() != null ? sale.getSaleChannel() : "PADRAO",
                    "CAMPANHA_DEFINITIVO",
                    sale.getTipoVenda() != null ? sale.getTipoVenda() : "INFORMADA",
                    sale.getIdLoteOrigem(),
                    "CAMPANHA#" + campanha.getId() + "-V" + versao.getNumero(),
                    snapshotParametros
            );
            logImutavel.setOrigemTaxa(processamento.origemTaxa());
            logImutavel.setValorBonusPercentual(processamento.parcelaBonusPerc());
            logImutavel.setValorBonusFixo(processamento.parcelaBonusFixo());
            logImutavel.setUsuarioExecutor(request.executadoPor());

            logsParaSalvar.add(logImutavel);

            totalVendas = totalVendas.add(resultado.getValorVenda());
            totalComissao = totalComissao.add(resultado.getValorComissao());
            totalBonus = totalBonus.add(processamento.parcelaBonusPerc().add(processamento.parcelaBonusFixo()));
        }

        // 9. Persistência da apuração definitiva e consolidação dos totais
        CampanhaApuracao apuracao = new CampanhaApuracao();
        apuracao.setCampanha(campanha);
        apuracao.setCampanhaVersao(versao);
        apuracao.setProtocolo(UUID.randomUUID());
        apuracao.setCompetencia(competenciaStr);
        apuracao.setDataInicio(dataInicio);
        apuracao.setDataFim(dataFim);
        apuracao.setVersaoCampanhaNumero(versao.getNumero());
        apuracao.setVersaoProgramaPython(programa.getVersaoPrograma());
        apuracao.setChecksumPython(programa.getChecksumSha256());
        apuracao.setExecutadoViaPython(true);
        apuracao.setTotalVendasProcessadas(vendasCampanha.size());
        apuracao.setTotalCalculados(resultadosParaSalvar.size());
        apuracao.setTotalImpedimentos(impedimentos.size());
        apuracao.setValorTotalVendas(totalVendas);
        apuracao.setValorTotalComissao(totalComissao);
        apuracao.setValorTotalBonus(totalBonus);
        apuracao.setExecutadoPor(request.executadoPor());
        apuracao.setExecutadoEm(OffsetDateTime.now());
        apuracao.setStatus("CONCLUIDO");
        apuracao.setObservacoes(request.observacoes());

        apuracao = apuracaoRepository.save(apuracao);

        // Vincula o ID da apuração aos resultados calculados
        for (ResultadoCalculo r : resultadosParaSalvar) {
            r.setApuracaoCampanhaId(apuracao.getId());
        }

        // 10. Persistência em blocos
        salvarEmBlocos(resultadosParaSalvar, logsParaSalvar);

        // 11. Atualização do progresso da esteira (Etapa 4 - Concluída / Apurada)
        versao.setUltimaEtapa(Math.max(versao.getUltimaEtapa(), 4));
        versao.setEtapaMaisAvancada(Math.max(versao.getEtapaMaisAvancada(), 4));
        campanhaVersaoRepository.save(versao);

        if (transicaoRepository != null) {
            transicaoRepository.save(new CampanhaEsteiraTransicao(
                    versao,
                    versao.getUltimaEtapa(),
                    4,
                    versao.getEstadoEsteira(),
                    versao.getEstadoEsteira(),
                    TipoAcaoEsteira.AVANCO_ETAPA,
                    request.executadoPor(),
                    String.format("Apuração definitiva executada com sucesso via motor Python (v%d - %s). Protocolo: %s",
                            programa.getVersaoPrograma(),
                            programa.getChecksumSha256() != null && programa.getChecksumSha256().length() >= 8
                                    ? programa.getChecksumSha256().substring(0, 8) : "hash",
                            apuracao.getProtocolo())
            ));
        }

        if (auditoriaService != null) {
            auditoriaService.registrarAcao(
                    request.executadoPor(),
                    TipoAcaoAuditoria.CALCULO_DEFINITIVO_CAMPANHA,
                    TipoEntidadeAuditoria.APURACAO,
                    String.valueOf(campanhaId),
                    versao.getNumero(),
                    String.format("Apuração definitiva executada com sucesso. Protocolo: %s, Total Vendas: %d, Sucessos: %d, Impedimentos: %d",
                            apuracao.getProtocolo(), vendasCampanha.size(), resultadosParaSalvar.size(), impedimentos.size()),
                    String.format("{\"protocolo\": \"%s\", \"totalVendas\": %d, \"totalComissao\": \"%s\"}",
                            apuracao.getProtocolo(), vendasCampanha.size(), totalComissao)
            );
        }

        log.info("Cálculo definitivo concluído com sucesso para campanha {} versão {}. Protocolo: {}, Sucessos: {}, Impedimentos: {}",
                campanhaId, versao.getNumero(), apuracao.getProtocolo(), resultadosParaSalvar.size(), impedimentos.size());

        List<ItemApuracaoResponse> itensResponse = resultadosParaSalvar.stream()
                .map(this::converterParaItemResponse)
                .toList();

        return new ApuracaoCampanhaResponse(
                apuracao.getId(),
                apuracao.getProtocolo(),
                campanha.getId(),
                versao.getId(),
                versao.getNumero(),
                apuracao.getCompetencia(),
                apuracao.getDataInicio(),
                apuracao.getDataFim(),
                apuracao.getVersaoProgramaPython(),
                apuracao.getChecksumPython(),
                apuracao.isExecutadoViaPython(),
                apuracao.getTotalVendasProcessadas(),
                apuracao.getTotalCalculados(),
                apuracao.getTotalImpedimentos(),
                apuracao.getValorTotalVendas(),
                apuracao.getValorTotalComissao(),
                apuracao.getValorTotalBonus(),
                apuracao.getExecutadoPor(),
                apuracao.getExecutadoEm(),
                apuracao.getStatus(),
                apuracao.getObservacoes(),
                itensResponse,
                impedimentos
        );
    }

    /**
     * Consulta a apuração definitiva mais recente executada para a campanha.
     */
    @Transactional(readOnly = true)
    public ApuracaoCampanhaResponse obterUltimaApuracao(Long campanhaId) {
        CampanhaApuracao apuracao = apuracaoRepository.findFirstByCampanhaIdOrderByExecutadoEmDesc(campanhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma apuração definitiva encontrada para a campanha ID: " + campanhaId));

        return montarRespostaCompleta(apuracao);
    }

    /**
     * Consulta uma apuração específica pelo seu identificador único.
     */
    @Transactional(readOnly = true)
    public ApuracaoCampanhaResponse obterApuracaoPorId(Long campanhaId, UUID apuracaoId) {
        CampanhaApuracao apuracao = apuracaoRepository.findById(apuracaoId)
                .orElseThrow(() -> new ResourceNotFoundException("Apuração não encontrada com ID: " + apuracaoId));

        if (apuracao.getCampanha() == null || !campanhaId.equals(apuracao.getCampanha().getId())) {
            throw new BusinessException("A apuração informada não pertence à campanha " + campanhaId);
        }

        return montarRespostaCompleta(apuracao);
    }

    /**
     * Lista o histórico resumido de todas as apurações definitivas da campanha.
     */
    @Transactional(readOnly = true)
    public List<ApuracaoResumoDTO> listarHistoricoApuracoes(Long campanhaId) {
        return apuracaoRepository.findAllByCampanhaIdOrderByExecutadoEmDesc(campanhaId).stream()
                .map(this::converterParaResumoDTO)
                .toList();
    }

    private ApuracaoCampanhaResponse montarRespostaCompleta(CampanhaApuracao apuracao) {
        List<ResultadoCalculo> resultados = resultadoCalculoRepository != null
                ? resultadoCalculoRepository.findAllByApuracaoCampanhaIdOrderByDataVendaAsc(apuracao.getId())
                : List.of();

        List<ItemApuracaoResponse> itens = resultados.stream()
                .map(this::converterParaItemResponse)
                .toList();

        return new ApuracaoCampanhaResponse(
                apuracao.getId(),
                apuracao.getProtocolo(),
                apuracao.getCampanha() != null ? apuracao.getCampanha().getId() : null,
                apuracao.getCampanhaVersao() != null ? apuracao.getCampanhaVersao().getId() : null,
                apuracao.getVersaoCampanhaNumero(),
                apuracao.getCompetencia(),
                apuracao.getDataInicio(),
                apuracao.getDataFim(),
                apuracao.getVersaoProgramaPython(),
                apuracao.getChecksumPython(),
                apuracao.isExecutadoViaPython(),
                apuracao.getTotalVendasProcessadas(),
                apuracao.getTotalCalculados(),
                apuracao.getTotalImpedimentos(),
                apuracao.getValorTotalVendas(),
                apuracao.getValorTotalComissao(),
                apuracao.getValorTotalBonus(),
                apuracao.getExecutadoPor(),
                apuracao.getExecutadoEm(),
                apuracao.getStatus(),
                apuracao.getObservacoes(),
                itens,
                List.of()
        );
    }

    private ApuracaoResumoDTO converterParaResumoDTO(CampanhaApuracao a) {
        return new ApuracaoResumoDTO(
                a.getId(),
                a.getProtocolo(),
                a.getCampanha() != null ? a.getCampanha().getId() : null,
                a.getVersaoCampanhaNumero(),
                a.getCompetencia(),
                a.getDataInicio(),
                a.getDataFim(),
                a.getVersaoProgramaPython(),
                a.getChecksumPython(),
                a.isExecutadoViaPython(),
                a.getTotalVendasProcessadas(),
                a.getTotalCalculados(),
                a.getTotalImpedimentos(),
                a.getValorTotalVendas(),
                a.getValorTotalComissao(),
                a.getValorTotalBonus(),
                a.getExecutadoPor(),
                a.getExecutadoEm(),
                a.getStatus()
        );
    }

    private ItemApuracaoResponse converterParaItemResponse(ResultadoCalculo r) {
        return new ItemApuracaoResponse(
                r.getId(),
                r.getProtocoloCalculo(),
                r.getIdVenda(),
                r.getMatricula(),
                r.getCodMarca(),
                r.getCodLoja(),
                r.getCodCargo(),
                r.getDataVenda(),
                r.getValorVenda(),
                r.getTaxaAplicada(),
                r.getValorComissao(),
                r.getValorBonusPercentual(),
                r.getValorBonusFixo(),
                r.getRegraId(),
                r.getOrigemTaxa(),
                r.getCalculadoEm()
        );
    }

    private record ItemProcessamentoVenda(
            boolean valida,
            RegraItemRequest regraVencedora,
            BigDecimal taxaFinal,
            BigDecimal parcelaBase,
            BigDecimal parcelaBonusPerc,
            BigDecimal parcelaBonusFixo,
            BigDecimal valorComissao,
            Long regraId,
            String origemTaxa,
            List<String> impedimentos
    ) {}

    private ItemProcessamentoVenda processarVenda(
            Sale sale,
            List<RegraItemRequest> regras,
            List<BaseComiss> basesComissao
    ) {
        List<String> impedimentos = new ArrayList<>();
        Registration reg = sale.getRegistration();
        Position pos = (reg != null) ? reg.getPosition() : null;
        Brand brand = sale.getBrand();
        BigDecimal valor = sale.getValue() != null ? sale.getValue() : BigDecimal.ZERO;

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
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
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

        BigDecimal taxaBaseContratual = BigDecimal.ZERO;
        if (brand != null && pos != null) {
            Optional<BigDecimal> baseOpt = buscarTaxaBase(brand, pos, sale.getSaleDate(), basesComissao);
            if (baseOpt.isEmpty()) {
                impedimentos.add(String.format("Taxa base contratual ausente em tb_basecomiss para Marca '%s' e Cargo '%s'",
                        brand.getDescription() != null ? brand.getDescription() : brand.getCode(),
                        pos.getDescription() != null ? pos.getDescription() : pos.getCode()));
            } else {
                taxaBaseContratual = baseOpt.get().setScale(4, RoundingMode.HALF_UP);
            }
        }

        if (!impedimentos.isEmpty()) {
            return new ItemProcessamentoVenda(
                    false,
                    null,
                    taxaBaseContratual,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    null,
                    impedimentos
            );
        }

        // Filtra regras que cobrem o público e a faixa de valor da venda
        List<RegraItemRequest> regrasPublico = regras.stream()
                .filter(r -> referenciaPublicoService.atendePublico(r, sale))
                .toList();

        List<RegraItemRequest> regrasFaixa = regrasPublico.stream()
                .filter(r -> referenciaPublicoService.atendeFaixaValor(r, valor))
                .toList();

        if (regrasFaixa.isEmpty()) {
            BigDecimal comissaoFallback = valor.multiply(taxaBaseContratual).setScale(2, RoundingMode.HALF_UP);
            return new ItemProcessamentoVenda(
                    true,
                    null,
                    taxaBaseContratual,
                    comissaoFallback,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    comissaoFallback,
                    null,
                    "BASE_COMISS",
                    List.of()
            );
        }

        // Seleção pela regra mais específica (especificidade das dimensões de público)
        int maxEsp = regrasFaixa.stream()
                .mapToInt(referenciaPublicoService::calcularEspecificidade)
                .max()
                .orElse(0);

        List<RegraItemRequest> maisEspecificas = regrasFaixa.stream()
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

        BigDecimal parcelaBase = valor.multiply(taxaFinal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal parcelaBonusPerc = (vencedora.bonusPercentual() != null && vencedora.bonusPercentual().compareTo(BigDecimal.ZERO) > 0)
                ? valor.multiply(vencedora.bonusPercentual()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal parcelaBonusFixo = (vencedora.bonusValorFixo() != null && vencedora.bonusValorFixo().compareTo(BigDecimal.ZERO) > 0)
                ? vencedora.bonusValorFixo().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorComissao = parcelaBase.add(parcelaBonusPerc).add(parcelaBonusFixo);
        Long regraId = extrairIdNumerico(vencedora.blocoId());

        return new ItemProcessamentoVenda(
                true,
                vencedora,
                taxaFinal,
                parcelaBase,
                parcelaBonusPerc,
                parcelaBonusFixo,
                valorComissao,
                regraId,
                "REGRA_CAMPANHA",
                List.of()
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

    private String montarSnapshot(
            ResultadoCalculo resultado,
            ItemProcessamentoVenda processamento,
            CampanhaVersao versao,
            CampanhaProgramaPython programa
    ) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("campanhaId", resultado.getCampanhaId());
            map.put("versaoCampanhaNumero", versao.getNumero());
            map.put("versaoProgramaPython", programa.getVersaoPrograma());
            map.put("checksumPython", programa.getChecksumSha256());
            map.put("idVenda", resultado.getIdVenda());
            map.put("matricula", resultado.getMatricula());
            map.put("valorVenda", resultado.getValorVenda());
            map.put("taxaAplicada", resultado.getTaxaAplicada());
            map.put("valorComissao", resultado.getValorComissao());
            map.put("valorBonusPercentual", resultado.getValorBonusPercentual());
            map.put("valorBonusFixo", resultado.getValorBonusFixo());
            map.put("origemTaxa", resultado.getOrigemTaxa());
            map.put("regraId", resultado.getRegraId());
            if (processamento.regraVencedora() != null) {
                map.put("regraNome", processamento.regraVencedora().nome());
                map.put("tipoOperacao", processamento.regraVencedora().tipoOperacao());
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void salvarEmBlocos(List<ResultadoCalculo> resultados, List<LogCalculoImutavel> logs) {
        if (resultados != null && !resultados.isEmpty() && resultadoCalculoRepository != null) {
            for (int i = 0; i < resultados.size(); i += TAMANHO_BLOCO) {
                int fimBloco = Math.min(i + TAMANHO_BLOCO, resultados.size());
                resultadoCalculoRepository.saveAll(resultados.subList(i, fimBloco));
            }
        }

        if (logs != null && !logs.isEmpty() && logCalculoRepository != null) {
            for (int i = 0; i < logs.size(); i += TAMANHO_BLOCO) {
                int fimBloco = Math.min(i + TAMANHO_BLOCO, logs.size());
                logCalculoRepository.saveAll(logs.subList(i, fimBloco));
            }
        }
    }

    private void validarTravaFechamento(String competencia, Boolean forcarRecalculo) {
        if (Boolean.TRUE.equals(forcarRecalculo)) {
            return;
        }
        if (fechamentoCompetenciaRepository != null && fechamentoCompetenciaRepository.existsByCompetenciaAndStatus(competencia, StatusFechamento.CONCLUIDO)) {
            throw new BusinessException(String.format(
                    "A competência '%s' possui fechamento final formalizado (CONCLUIDO). Não é permitido novo cálculo definitivo sem desbloqueio formal ou flag forcarRecalculo.",
                    competencia
            ));
        }
    }

    private String resolverCompetencia(String compInformada, CampanhaVersao versao) {
        if (compInformada != null && !compInformada.isBlank()) {
            return normalizarCompetencia(compInformada);
        }
        if (versao != null && versao.getDataInicio() != null) {
            return YearMonth.from(versao.getDataInicio()).toString();
        }
        return YearMonth.now().toString();
    }

    private String normalizarCompetencia(String comp) {
        String limpo = comp.trim();
        if (limpo.length() >= 7) {
            return limpo.substring(0, 7);
        }
        throw new BusinessException(String.format("Formato de competência inválido: '%s'. Utilize YYYY-MM.", comp));
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

    private CampanhaVersao resolverVersao(Campanha campanha, Integer versaoNumero) {
        if (versaoNumero != null) {
            return campanhaVersaoRepository.findByCampanhaIdAndNumero(campanha.getId(), versaoNumero)
                    .orElseThrow(() -> new ResourceNotFoundException(String.format("Versão %d da campanha %d não encontrada.", versaoNumero, campanha.getId())));
        }
        CampanhaVersao vigente = campanha.getVersaoVigente();
        if (vigente != null) {
            return vigente;
        }
        CampanhaVersao proposta = campanha.getVersaoProposta();
        if (proposta != null) {
            return proposta;
        }
        return campanhaVersaoRepository.findFirstByCampanhaIdOrderByNumeroDesc(campanha.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma versão encontrada para a campanha " + campanha.getId()));
    }

    private RegraItemRequest converterRegraParaItemRequest(Regra r) {
        return new RegraItemRequest(
                r.getBlocoId() != null && !r.getBlocoId().isBlank() ? r.getBlocoId() : "bloco-" + r.getId(),
                r.getTrechoOrigem(),
                r.getNome(),
                r.getCanal(),
                r.getCodMarca(),
                r.getDescrMarca(),
                r.getCodLoja(),
                r.getCodCargo(),
                r.getDescriCargo(),
                r.getMatricula(),
                r.getValorMinimo(),
                r.getMinInclusivo(),
                r.getValorMaximo(),
                r.getMaxInclusivo(),
                r.getTipoOperacao(),
                r.getValorAjuste(),
                r.getTipoBaseReferencia(),
                r.getTaxaBaseConsultada(),
                r.getDescricaoReferencia(),
                r.getTaxa(),
                r.getExplicacao(),
                r.getPythonEquivalente(),
                List.of(),
                r.getCompleta(),
                r.getStatus(),
                r.getBonusPercentual(),
                r.getBonusValorFixo()
        );
    }
}
