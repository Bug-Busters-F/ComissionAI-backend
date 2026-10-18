package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;

/**
 * Serviço responsável pela simulação em sandbox utilizando o motor de cálculo compartilhado (BACK-6).
 *
 * Características e regras de negócio:
 * 1. Simulação 100% em memória (sandbox): NÃO grava resultados em tb_resultado_calculo,
 *    NÃO gera logs em tb_log_calculo_imutavel e NÃO altera datas ou valores em tb_sales.
 * 2. Recorte de vendas: opera sobre a união de públicos da proposta na competência histórica informada.
 * 3. Cenário Base (Documento): calcula a comissão pela taxa padrão de tb_basecomiss para Marca + Cargo,
 *    ignorando regras promocionais de outras campanhas ativas em produção.
 * 4. Cenário Proposta (100%): aplica hipoteticamente as regras propostas sem alterar datas históricas.
 *    Se nenhuma regra se aplicar a uma venda, recorre ao fallback da taxa base contratual do Cenário 1.
 * 5. Cenários de Sensibilidade (80%, 120%, 150%): recalcula por venda escalando o valor bruto,
 *    reavaliando dinamicamente as faixas monetárias (inclusive transição/mudança de faixa).
 * 6. Projeção temporal: pro-rata calculada por (dias inclusivos da campanha ÷ 30).
 * 7. Precisão decimal e arredondamento idênticos ao motor de produção (HALF_UP, escala 2 para moeda e 4 para taxa).
 */
@Service
public class SimulacaoCenariosService {

    private static final Logger log = LoggerFactory.getLogger(SimulacaoCenariosService.class);

    private static final BigDecimal FATOR_80 = new BigDecimal("0.80");
    private static final BigDecimal FATOR_100 = new BigDecimal("1.00");
    private static final BigDecimal FATOR_120 = new BigDecimal("1.20");
    private static final BigDecimal FATOR_150 = new BigDecimal("1.50");
    private static final BigDecimal DIAS_BASE_MES = new BigDecimal("30");

    private final SaleRepository saleRepository;
    private final BaseComissRepository baseComissRepository;
    private final CampanhaRepository campanhaRepository;
    private final ReferenciaPublicoService referenciaPublicoService;

    public SimulacaoCenariosService(
            SaleRepository saleRepository,
            BaseComissRepository baseComissRepository,
            CampanhaRepository campanhaRepository,
            ReferenciaPublicoService referenciaPublicoService) {
        this.saleRepository = saleRepository;
        this.baseComissRepository = baseComissRepository;
        this.campanhaRepository = campanhaRepository;
        this.referenciaPublicoService = referenciaPublicoService;
    }

    /**
     * Executa a simulação completa de cenários e sensibilidade financeira em sandbox.
     */
    @Transactional(readOnly = true)
    public SimulacaoCenariosResponse simularCenarios(SimulacaoCenariosRequest request) {
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
            return construirRespostaVazia(competenciaStr, campanha, dataInicioCampanha, dataFimCampanha, diasInclusivos, fatorProjecao, avisosGerais);
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
            return construirRespostaVazia(competenciaStr, campanha, dataInicioCampanha, dataFimCampanha, diasInclusivos, fatorProjecao, avisosGerais);
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
        CenarioConsolidadoDTO cenario100 = consolidarCenario(todasVendasSimuladas, FATOR_100, "Cenário 100% (Proposta Revisada)", fatorProjecao, v -> v.cenario100());
        CenarioConsolidadoDTO cenario80 = consolidarCenario(todasVendasSimuladas, FATOR_80, "Cenário 80% (Volume Reduzido)", fatorProjecao, v -> v.cenario80());
        CenarioConsolidadoDTO cenario120 = consolidarCenario(todasVendasSimuladas, FATOR_120, "Cenário 120% (Crescimento Otimista)", fatorProjecao, v -> v.cenario120());
        CenarioConsolidadoDTO cenario150 = consolidarCenario(todasVendasSimuladas, FATOR_150, "Cenário 150% (Alta Performance)", fatorProjecao, v -> v.cenario150());

        // 7. Filtros e paginação da listagem de vendas
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

        return new SimulacaoCenariosResponse(
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
                avisosGerais
        );
    }

    // =========================================================================
    // MÉTODOS DE PROCESSAMENTO POR VENDA E RESOLUÇÃO DETERMINÍSTICA
    // =========================================================================

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
                impedimentos
        );
    }

    /**
     * Resolve determinística e hipoteticamente a comissão de uma venda para um volume monetário específico.
     * Reavalia os limites de faixa sobre o valor escalado, seleciona a regra de maior especificidade
     * e aplica o fallback contratual se nenhuma regra atender à faixa.
     */
    private VendaSimuladaCenarioItemDTO resolverCenarioVenda(
            BigDecimal valorVendaEscalado,
            BigDecimal fatorEscala,
            List<RegraItemRequest> regrasQueCobremPublico,
            BigDecimal taxaBaseContratual,
            VendaSimuladaCenarioItemDTO cenario100Referencia) {

        // Filtra regras que atendem à faixa monetária com o valor escalado desta simulação
        List<RegraItemRequest> regrasElegiveisNaFaixa = regrasQueCobremPublico.stream()
                .filter(r -> referenciaPublicoService.atendeFaixaValor(r, valorVendaEscalado))
                .toList();

        // Se nenhuma regra proposta atende à faixa: FALLBACK para a base do documento
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

        // Múltiplas regras aplicáveis na faixa: resolução de especificidade e desempate
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

        // Cálculo da comissão pelo motor compartilhado: valor × taxa + valor × bônus % + bônus fixo
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

    // =========================================================================
    // MÉTODOS DE CONSOLIDAÇÃO E PROJEÇÃO
    // =========================================================================

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
                avisos
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
}
