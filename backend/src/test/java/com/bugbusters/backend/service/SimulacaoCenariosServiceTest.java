package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.simulacao.*;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;

@ExtendWith(MockitoExtension.class)
class SimulacaoCenariosServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private BaseComissRepository baseComissRepository;

    @Mock
    private CampanhaRepository campanhaRepository;

    @Mock
    private RegraRepository regraRepository;

    private ReferenciaPublicoService referenciaPublicoService;
    private SimulacaoCenariosService service;

    private Brand brandPreto;
    private Store store75;
    private Position positionVendedor;
    private Registration regColaborador1;
    private BaseComiss baseComissVendedor;

    @BeforeEach
    void setUp() {
        referenciaPublicoService = new ReferenciaPublicoService(
                saleRepository,
                baseComissRepository,
                campanhaRepository,
                regraRepository
        );

        service = new SimulacaoCenariosService(
                saleRepository,
                baseComissRepository,
                campanhaRepository,
                referenciaPublicoService
        );

        brandPreto = new Brand();
        brandPreto.setId(UUID.randomUUID());
        brandPreto.setCode(10);
        brandPreto.setDescription("PRETO");

        store75 = new Store();
        store75.setId(UUID.randomUUID());
        store75.setCode(75);

        positionVendedor = new Position();
        positionVendedor.setId(UUID.randomUUID());
        positionVendedor.setCode(100);
        positionVendedor.setDescription("VENDEDOR LOJA");

        regColaborador1 = new Registration();
        regColaborador1.setId(UUID.randomUUID());
        regColaborador1.setRegistration("MATRIC-1");
        regColaborador1.setPosition(positionVendedor);
        regColaborador1.setStore(store75);
        regColaborador1.setAdmissDate(LocalDate.of(2025, 1, 1));

        baseComissVendedor = new BaseComiss(brandPreto, positionVendedor, new BigDecimal("0.0300"));
        baseComissVendedor.setId(UUID.randomUUID());
        baseComissVendedor.setReferenceMonth(LocalDate.of(2026, 9, 1));
    }

    private Sale criarVenda(UUID id, BigDecimal valor, LocalDate data, String canal) {
        Sale sale = new Sale();
        sale.setId(id != null ? id : UUID.randomUUID());
        sale.setValue(valor);
        sale.setSaleDate(data);
        sale.setSaleChannel(canal);
        sale.setBrand(brandPreto);
        sale.setStore(store75);
        sale.setRegistration(regColaborador1);
        return sale;
    }

    @Test
    @DisplayName("[BACK-6] Comparativo Base vs Proposta: calcular comissão pela base do documento e pela proposta revisada")
    void deveCalcularComparativoBaseVsProposta() {
        // Venda histórica de R$ 10.000,00 em 2026-09-15
        Sale sale = criarVenda(UUID.randomUUID(), new BigDecimal("10000.00"), LocalDate.of(2026, 9, 15), "ECOMMERCE");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any()))
                .thenReturn(List.of(sale));
        when(baseComissRepository.findAllComRelacionamentos())
                .thenReturn(List.of(baseComissVendedor));

        // Proposta revisada: 5% (0.0500) para canal ECOMMERCE
        RegraItemRequest regraProposta = new RegraItemRequest(
                "bloco-1", "5% ecommerce", "Regra E-commerce 5%", "ECOMMERCE",
                10, "PRETO", null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0500"),
                null, null, null, new BigDecimal("0.0500"),
                null, null, null, true, null,
                null, null
        );

        SimulacaoCenariosRequest request = new SimulacaoCenariosRequest(
                "2026-09", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                List.of(regraProposta),
                100, 0, false, false
        );

        SimulacaoCenariosResponse response = service.simularCenarios(request);

        assertThat(response.totalVendasRecortadas()).isEqualTo(1);
        assertThat(response.valorTotalRecortado()).isEqualByComparingTo("10000.00");

        // Cenário 100% Proposta vs Base
        CenarioConsolidadoDTO c100 = response.cenario100();
        assertThat(c100.totalComissaoBase()).isEqualByComparingTo("300.00"); // 10.000 * 3%
        assertThat(c100.totalComissaoProposta()).isEqualByComparingTo("500.00"); // 10.000 * 5%
        assertThat(c100.deltaComissao()).isEqualByComparingTo("200.00"); // +200.00
        assertThat(c100.deltaPercentual()).isEqualByComparingTo("66.67"); // (200 / 300) * 100 = 66.67%
        assertThat(c100.taxaMediaEfetivaBase()).isEqualByComparingTo("0.0300");
        assertThat(c100.taxaMediaEfetivaProposta()).isEqualByComparingTo("0.0500");

        // Detalhe da venda
        assertThat(response.vendas()).hasSize(1);
        VendaSimuladaDetalheDTO v = response.vendas().get(0);
        assertThat(v.taxaBaseContratual()).isEqualByComparingTo("0.0300");
        assertThat(v.valorComissaoBase()).isEqualByComparingTo("300.00");
        assertThat(v.cenario100().taxaAplicada()).isEqualByComparingTo("0.0500");
        assertThat(v.cenario100().valorComissao()).isEqualByComparingTo("500.00");
        assertThat(v.cenario100().origemTaxa()).isEqualTo("REGRA_PROPOSTA");
    }

    @Test
    @DisplayName("[BACK-6] Fallback para a Base do Documento: se nenhuma regra proposta se aplicar à venda, utilizar base do Cenário 1")
    void deveRecorrerAoFallbackDaBaseSeNenhumaRegraAplicar() {
        // Venda de R$ 3.000,00 no canal ECOMMERCE
        Sale sale = criarVenda(UUID.randomUUID(), new BigDecimal("3000.00"), LocalDate.of(2026, 9, 15), "ECOMMERCE");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any()))
                .thenReturn(List.of(sale));
        when(baseComissRepository.findAllComRelacionamentos())
                .thenReturn(List.of(baseComissVendedor));

        // Proposta de campanha: abrange ECOMMERCE, mas exige valor MÍNIMO de R$ 5.000,00
        RegraItemRequest regraMinima = new RegraItemRequest(
                "bloco-1", "faixa alta", "Regra Acima de 5k", "ECOMMERCE",
                10, "PRETO", null, null, null, null,
                new BigDecimal("5000.00"), true, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0600"),
                null, null, null, new BigDecimal("0.0600"),
                null, null, null, true, null,
                null, null
        );

        SimulacaoCenariosRequest request = new SimulacaoCenariosRequest(
                "2026-09", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                List.of(regraMinima),
                100, 0, false, false
        );

        SimulacaoCenariosResponse response = service.simularCenarios(request);

        assertThat(response.totalVendasRecortadas()).isEqualTo(1);
        CenarioConsolidadoDTO c100 = response.cenario100();
        // Como a venda de R$ 3.000 não atinge o mínimo de R$ 5.000, cai no fallback da base (3%)
        assertThat(c100.totalComissaoBase()).isEqualByComparingTo("90.00"); // 3.000 * 3%
        assertThat(c100.totalComissaoProposta()).isEqualByComparingTo("90.00"); // Fallback: 3.000 * 3%
        assertThat(c100.deltaComissao()).isEqualByComparingTo("0.00");
        assertThat(c100.qtdVendasComFallbackBase()).isEqualTo(1);
        assertThat(c100.qtdVendasComRegraAplicada()).isEqualTo(0);

        VendaSimuladaDetalheDTO v = response.vendas().get(0);
        assertThat(v.cenario100().origemTaxa()).isEqualTo("BASE_COMISS");
        assertThat(v.cenario100().taxaAplicada()).isEqualByComparingTo("0.0300");
        assertThat(v.cenario100().valorComissao()).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("[BACK-6] Sensibilidade e Mudança de Faixa nos cenários de 80%, 120% e 150%")
    void deveRecalcularCenariosSensibilidadeComMudancaDeFaixa() {
        // Venda original de R$ 1.800,00 no canal LOJA_FISICA
        Sale sale = criarVenda(UUID.randomUUID(), new BigDecimal("1800.00"), LocalDate.of(2026, 9, 10), "LOJA_FISICA");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any()))
                .thenReturn(List.of(sale));
        when(baseComissRepository.findAllComRelacionamentos())
                .thenReturn(List.of(baseComissVendedor));

        // Regra 1: faixa de R$ 1.000,00 a R$ 2.000,00 -> taxa 4%
        RegraItemRequest regraFaixa1 = new RegraItemRequest(
                "regra-1", "faixa 1", "Faixa 1k a 2k", "LOJA_FISICA",
                10, "PRETO", null, null, null, null,
                new BigDecimal("1000.00"), true, new BigDecimal("2000.00"), true,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0400"),
                null, null, null, new BigDecimal("0.0400"),
                null, null, null, true, null,
                null, null
        );

        // Regra 2: faixa de R$ 2.000,01 a R$ 5.000,00 -> taxa 6%
        RegraItemRequest regraFaixa2 = new RegraItemRequest(
                "regra-2", "faixa 2", "Faixa 2k a 5k", "LOJA_FISICA",
                10, "PRETO", null, null, null, null,
                new BigDecimal("2000.01"), true, new BigDecimal("5000.00"), true,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0600"),
                null, null, null, new BigDecimal("0.0600"),
                null, null, null, true, null,
                null, null
        );

        SimulacaoCenariosRequest request = new SimulacaoCenariosRequest(
                "2026-09", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                List.of(regraFaixa1, regraFaixa2),
                100, 0, false, false
        );

        SimulacaoCenariosResponse response = service.simularCenarios(request);

        VendaSimuladaDetalheDTO v = response.vendas().get(0);

        // Cenário 100%: R$ 1.800,00 -> entra na Faixa 1 (taxa 4%)
        assertThat(v.cenario100().valorVenda()).isEqualByComparingTo("1800.00");
        assertThat(v.cenario100().taxaAplicada()).isEqualByComparingTo("0.0400");
        assertThat(v.cenario100().valorComissao()).isEqualByComparingTo("72.00"); // 1800 * 4%
        assertThat(v.cenario100().regraNome()).isEqualTo("Faixa 1k a 2k");

        // Cenário 80%: R$ 1.800 * 0.80 = R$ 1.440,00 -> continua na Faixa 1 (taxa 4%)
        assertThat(v.cenario80().valorVenda()).isEqualByComparingTo("1440.00");
        assertThat(v.cenario80().taxaAplicada()).isEqualByComparingTo("0.0400");
        assertThat(v.cenario80().valorComissao()).isEqualByComparingTo("57.60"); // 1440 * 4%
        assertThat(v.cenario80().mudouFaixaEmRelacaoA100()).isFalse();

        // Cenário 120%: R$ 1.800 * 1.20 = R$ 2.160,00 -> MUDOU DE FAIXA! Entra na Faixa 2 (taxa 6%)
        assertThat(v.cenario120().valorVenda()).isEqualByComparingTo("2160.00");
        assertThat(v.cenario120().taxaAplicada()).isEqualByComparingTo("0.0600");
        assertThat(v.cenario120().valorComissao()).isEqualByComparingTo("129.60"); // 2160 * 6%
        assertThat(v.cenario120().regraNome()).isEqualTo("Faixa 2k a 5k");
        assertThat(v.cenario120().mudouFaixaEmRelacaoA100()).isTrue();

        // Cenário 150%: R$ 1.800 * 1.50 = R$ 2.700,00 -> continua na Faixa 2 (taxa 6%)
        assertThat(v.cenario150().valorVenda()).isEqualByComparingTo("2700.00");
        assertThat(v.cenario150().taxaAplicada()).isEqualByComparingTo("0.0600");
        assertThat(v.cenario150().valorComissao()).isEqualByComparingTo("162.00"); // 2700 * 6%
        assertThat(v.cenario150().mudouFaixaEmRelacaoA100()).isTrue();

        // Consolidados dos cenários
        assertThat(response.cenario120().qtdVendasComMudancaFaixa()).isEqualTo(1);
        assertThat(response.cenario150().qtdVendasComMudancaFaixa()).isEqualTo(1);
        assertThat(response.cenario80().qtdVendasComMudancaFaixa()).isEqualTo(0);
    }

    @Test
    @DisplayName("[BACK-6] Projeção Temporal Pro-Rata (dias inclusivos da campanha ÷ 30)")
    void deveProjetarComissaoPorDiasInclusivosDivididoPor30() {
        Sale sale = criarVenda(UUID.randomUUID(), new BigDecimal("20000.00"), LocalDate.of(2026, 9, 20), "ECOMMERCE");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any()))
                .thenReturn(List.of(sale));
        when(baseComissRepository.findAllComRelacionamentos())
                .thenReturn(List.of(baseComissVendedor));

        RegraItemRequest regra = new RegraItemRequest(
                "bloco-1", "5% ecommerce", "Regra 5%", "ECOMMERCE",
                10, "PRETO", null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0500"),
                null, null, null, new BigDecimal("0.0500"),
                null, null, null, true, null,
                null, null
        );

        // Campanha de 15 dias inclusivos: 2026-11-01 a 2026-11-15 -> 15 dias -> fator 15 / 30 = 0.500000
        SimulacaoCenariosRequest request = new SimulacaoCenariosRequest(
                "2026-09", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15),
                List.of(regra),
                100, 0, false, false
        );

        SimulacaoCenariosResponse response = service.simularCenarios(request);

        assertThat(response.diasInclusivos()).isEqualTo(15);
        assertThat(response.fatorProjecao()).isEqualByComparingTo("0.500000");

        CenarioConsolidadoDTO c100 = response.cenario100();
        // Venda 20.000 * 5% = 1.000,00 comissão no mês
        assertThat(c100.totalComissaoProposta()).isEqualByComparingTo("1000.00");
        // Base: 20.000 * 3% = 600,00
        assertThat(c100.totalComissaoBase()).isEqualByComparingTo("600.00");
        // Delta mês = 400,00
        assertThat(c100.deltaComissao()).isEqualByComparingTo("400.00");

        // Projeção temporal: 1.000 * 0.50 = 500.00
        assertThat(c100.comissaoProjetada()).isEqualByComparingTo("500.00");
        // Delta projetado: 400 * 0.50 = 200.00
        assertThat(c100.deltaProjetado()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("[BACK-6] Cálculo com Bônus Percentual e Bônus Fixo pelo motor compartilhado")
    void deveCalcularBonusPercentualEFixoConformeMotorCompartilhado() {
        Sale sale = criarVenda(UUID.randomUUID(), new BigDecimal("1000.00"), LocalDate.of(2026, 9, 5), "ECOMMERCE");

        when(saleRepository.findVendasPorPeriodoComRelacionamentos(any(), any()))
                .thenReturn(List.of(sale));
        when(baseComissRepository.findAllComRelacionamentos())
                .thenReturn(List.of(baseComissVendedor));

        // Regra com taxa 5%, bônus percentual de 1% (0.0100) e bônus fixo de R$ 50,00
        RegraItemRequest regraComBonus = new RegraItemRequest(
                "bloco-1", "com bonus", "Regra Com Bônus", "ECOMMERCE",
                10, "PRETO", null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0500"),
                null, null, null, new BigDecimal("0.0500"),
                null, null, null, true, null,
                new BigDecimal("0.0100"), new BigDecimal("50.00")
        );

        SimulacaoCenariosRequest request = new SimulacaoCenariosRequest(
                "2026-09", null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                List.of(regraComBonus),
                100, 0, false, false
        );

        SimulacaoCenariosResponse response = service.simularCenarios(request);

        VendaSimuladaDetalheDTO v = response.vendas().get(0);
        // Base: 1000 * 5% = 50.00
        // Bonus perc: 1000 * 1% = 10.00
        // Bonus fixo: 50.00
        // Total = 50 + 10 + 50 = 110.00
        assertThat(v.cenario100().valorBonusPercentual()).isEqualByComparingTo("10.00");
        assertThat(v.cenario100().valorBonusFixo()).isEqualByComparingTo("50.00");
        assertThat(v.cenario100().valorComissao()).isEqualByComparingTo("110.00");
    }
}
