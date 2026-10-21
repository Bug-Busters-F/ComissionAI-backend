package com.bugbusters.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.calculo.CalculoComissaoRequest;
import com.bugbusters.backend.dto.calculo.CalculoComissaoResponse;
import com.bugbusters.backend.dto.calculo.CalculoCompetenciaResponseDTO;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

/**
 * Validação de Entrega BACK-14:
 * 1. Correção do cálculo avulso substituindo a taxa fixa de 10% pelo motor compartilhado (TaxaComissaoResolver).
 * 2. Precedência de regras de negócio sobre BaseComiss no cálculo avulso.
 * 3. Validação de vínculos obrigatórios e impedimentos contratuais.
 * 4. Paridade financeira centavo a centavo (escala 2, HALF_UP).
 * 5. Medição de desempenho em competência representativa com redução de consultas e processamento em blocos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalculoDesempenhoEParidadeTest {

    @Autowired
    private CalculoService calculoService;

    @Autowired
    private TaxaComissaoResolver taxaComissaoResolver;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private LogCalculoRepository logCalculoRepository;

    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;

    @BeforeEach
    void setUp() {
        brand = brandRepository.findByCode(20).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(20);
            b.setDescription("AZUL");
            return brandRepository.save(b);
        });

        store = storeRepository.findByCode(80).orElseGet(() -> {
            Store s = new Store();
            s.setCode(80);
            s.setDescription("LOJA SHOPPING");
            return storeRepository.save(s);
        });

        position = positionRepository.findByCode(250).orElseGet(() -> {
            Position p = new Position();
            p.setCode(250);
            p.setDescription("CONSULTOR");
            return positionRepository.save(p);
        });

        registration = registrationRepository.findByRegistration("COLAB-PERF-1").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("COLAB-PERF-1");
            r.setStore(store);
            r.setPosition(position);
            r.setAdmissDate(LocalDate.of(2025, 1, 1));
            return registrationRepository.save(r);
        });

        // Configura taxa base de 4% (0.0400) em tb_basecomiss para as competências do teste
        if (baseComissRepository.findByBrandCodeAndPositionCodeAndReferenceMonth(20, 250, LocalDate.of(2026, 9, 1)).isEmpty()) {
            BaseComiss bc1 = new BaseComiss(brand, position, new BigDecimal("0.0400"), LocalDate.of(2026, 9, 1));
            baseComissRepository.save(bc1);
        }
        if (baseComissRepository.findByBrandCodeAndPositionCodeAndReferenceMonth(20, 250, LocalDate.of(2027, 5, 1)).isEmpty()) {
            BaseComiss bc2 = new BaseComiss(brand, position, new BigDecimal("0.0400"), LocalDate.of(2027, 5, 1));
            baseComissRepository.save(bc2);
        }
    }

    @Test
    @DisplayName("BACK-14 / Avulso: Deve resolver taxa dinâmica de 4% da BaseComiss (em vez do antigo 10% fixo)")
    void deveResolverTaxaDinamicaBaseComissNoCalculoAvulso() {
        UUID idVenda = UUID.randomUUID();
        CalculoComissaoRequest request = new CalculoComissaoRequest(
                idVenda,
                "COLAB-PERF-1",
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 9, 15),
                20,
                "AZUL",
                80,
                "LOJA_FISICA"
        );

        CalculoComissaoResponse response = calculoService.calcularComissao(request);

        assertNotNull(response);
        // Taxa resolvida dinamicamente: 0.0400 (4%), comissão = R$ 40,00 (NÃO 100,00 do antigo hardcoded 10%)
        assertEquals(new BigDecimal("0.0400"), response.taxaAplicada(), "Taxa deve ser 4% de BaseComiss e não o antigo 10% fixo");
        assertEquals(new BigDecimal("40.00"), response.valorComissao(), "Comissão de R$ 1.000 a 4% deve ser R$ 40,00");
        assertEquals("COLAB-PERF-1", response.matricula());

        // Validação da auditoria imutável (BUG-23 / S1-B10)
        List<LogCalculoImutavel> logs = logCalculoRepository.findByProtocolo(response.protocoloCalculo());
        assertEquals(1, logs.size());
        LogCalculoImutavel log = logs.get(0);
        assertEquals(new BigDecimal("0.0400"), log.getTaxaAplicada());
        assertEquals(new BigDecimal("40.00"), log.getValorComissao());
        assertEquals("BASE_COMISS", log.getOrigemTaxa());
        assertTrue(log.getParametrosAplicados().contains("\"taxaAplicada\":0.0400"));
    }

    @Test
    @DisplayName("BACK-14 / Precedência: Regra de negócio ativa deve ter precedência sobre BaseComiss no cálculo avulso")
    void deveAplicarRegraAtivaComPrecedenciaNoCalculoAvulso() {
        Campanha campanha = new Campanha();
        campanha.setTitulo("Campanha Primavera");
        campanha.setTextoOriginal("Campanha promocional de primavera");
        campanha.setEstado(com.bugbusters.backend.model.EstadoCampanha.ATIVA);
        campanha.setDataInicio(LocalDate.of(2026, 9, 1));
        campanha.setDataFim(LocalDate.of(2026, 9, 30));
        campanha = campanhaRepository.save(campanha);

        Regra regraPromo = new Regra();
        regraPromo.setCampanha(campanha);
        regraPromo.setNome("Promoção Primavera 8%");
        regraPromo.setTaxa(new BigDecimal("0.0800"));
        regraPromo.setStatus(StatusRegra.ATIVA);
        regraPromo.setDataInicio(LocalDate.of(2026, 9, 1));
        regraPromo.setDataFim(LocalDate.of(2026, 9, 30));
        regraPromo.setCodMarca(20);
        regraPromo = regraRepository.save(regraPromo);

        UUID idVenda = UUID.randomUUID();
        CalculoComissaoRequest request = new CalculoComissaoRequest(
                idVenda,
                "COLAB-PERF-1",
                new BigDecimal("2500.00"),
                LocalDate.of(2026, 9, 15),
                20,
                "AZUL",
                80,
                "LOJA_FISICA"
        );

        CalculoComissaoResponse response = calculoService.calcularComissao(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("0.0800"), response.taxaAplicada(), "Regra ativa de 8% deve sobrepor os 4% da BaseComiss");
        assertEquals(new BigDecimal("200.00"), response.valorComissao(), "Comissão de R$ 2.500 a 8% deve ser R$ 200,00");
        assertEquals(regraPromo.getId(), response.idRegraAplicada());
    }

    @Test
    @DisplayName("BACK-14 / Impedimento: Deve rejeitar cálculo avulso quando vínculos essenciais forem inválidos")
    void deveRejeitarCalculoAvulsoSemVinculo() {
        UUID idVenda = UUID.randomUUID();
        CalculoComissaoRequest request = new CalculoComissaoRequest(
                idVenda,
                "COLAB-INEXISTENTE",
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 9, 15),
                20,
                "AZUL",
                80,
                "LOJA_FISICA"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                calculoService.calcularComissao(request)
        );

        assertTrue(ex.getMessage().contains("sem vínculo")
                || ex.getMessage().contains("não informado")
                || ex.getMessage().contains("cargo")
                || ex.getMessage().contains("cobertura"));
    }

    @Test
    @DisplayName("BACK-14 / Desempenho e Paridade Financeira: Medição de competência com 50 vendas e validação centavo a centavo")
    void deveMedirDesempenhoEParidadeFinanceiraEmCompetencia() {
        int quantidadeVendas = 50;
        List<Sale> vendasLote = new ArrayList<>();
        BigDecimal totalEsperadoVendas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalEsperadoComissao = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Gera 50 vendas distribuídas ao longo de 2026-09
        for (int i = 1; i <= quantidadeVendas; i++) {
            BigDecimal valor = new BigDecimal(100 + (i * 15)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal comissaoEsperada = valor.multiply(new BigDecimal("0.0400")).setScale(2, RoundingMode.HALF_UP);

            Sale sale = new Sale();
            sale.setId(UUID.randomUUID());
            sale.setRegistration(registration);
            sale.setBrand(brand);
            sale.setStore(store);
            sale.setValue(valor);
            sale.setSaleDate(LocalDate.of(2027, 5, (i % 28) + 1));
            sale.setSaleChannel("LOJA_FISICA");
            sale.setTipoVenda("IMPORTADA");

            vendasLote.add(sale);
            totalEsperadoVendas = totalEsperadoVendas.add(valor);
            totalEsperadoComissao = totalEsperadoComissao.add(comissaoEsperada);
        }

        saleRepository.saveAllAndFlush(vendasLote);

        // Medição de tempo e execução do cálculo consolidado com o motor otimizado
        long inicioTempo = System.currentTimeMillis();
        CalculoCompetenciaResponseDTO resultado = calculoService.calcularPorCompetencia("2027-05");
        long tempoTotalMs = System.currentTimeMillis() - inicioTempo;

        // Asserções de integridade e resultado
        assertNotNull(resultado);
        assertEquals(quantidadeVendas, resultado.totalCalculados(), "Todas as vendas devem ser calculadas");
        assertEquals(0, resultado.totalImpedimentos(), "Nenhum impedimento esperado");

        // Paridade financeira centavo a centavo (escala 2, RoundingMode.HALF_UP)
        assertEquals(totalEsperadoVendas, resultado.valorTotalVendas(), "Soma total de vendas deve ser exata centavo a centavo");
        assertEquals(totalEsperadoComissao, resultado.valorTotalComissao(), "Soma total de comissão deve ser exata centavo a centavo");

        // Validação de desempenho: 50 vendas processadas em menos de 3000ms graças ao pré-carregamento em lote
        assertTrue(tempoTotalMs < 3000, "O processamento em lote deve ser rápido (executado em " + tempoTotalMs + " ms)");
    }
}
