package com.bugbusters.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EstadoCampanha;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalculoMotorAvancadoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private LogCalculoRepository logCalculoRepository;

    private MockMvc mockMvc;

    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;
    private Campanha campanha;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        logCalculoRepository.deleteAllInBatch();
        resultadoCalculoRepository.deleteAllInBatch();
        saleRepository.deleteAllInBatch();
        regraRepository.deleteAllInBatch();
        campanhaRepository.deleteAllInBatch();
        baseComissRepository.deleteAllInBatch();
        registrationRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        positionRepository.deleteAllInBatch();
        brandRepository.deleteAllInBatch();

        brand = new Brand();
        brand.setCode(10);
        brand.setDescription("PRETO");
        brand = brandRepository.save(brand);

        store = new Store();
        store.setCode(75);
        store.setDescription("LOJA-75");
        store = storeRepository.save(store);

        position = new Position();
        position.setCode(100);
        position.setDescription("VENDEDOR LOJA");
        position = positionRepository.save(position);

        registration = new Registration();
        registration.setRegistration("MATRIC-ADV-01");
        registration.setStore(store);
        registration.setPosition(position);
        registration.setAdmissDate(LocalDate.of(2025, 1, 1));
        registration = registrationRepository.save(registration);

        BaseComiss baseComiss = new BaseComiss(brand, position, new BigDecimal("0.0300"));
        baseComiss.setReferenceMonth(LocalDate.of(2026, 9, 1));
        baseComissRepository.save(baseComiss);

        campanha = new Campanha(
                "Campanha Motor Avancado",
                "Texto de teste",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
        campanha.setEstado(EstadoCampanha.ATIVA);
        campanha = campanhaRepository.save(campanha);
    }

    @Test
    @DisplayName("1. Faixa de Valor: Deve aplicar regra com limite inclusivo quando venda estiver na fronteira")
    void deveAplicarRegraComLimiteInclusivoNaFronteira() throws Exception {
        Regra regra = new Regra(campanha, "Regra Faixa 1K a 5K", "ECOMMERCE", new BigDecimal("0.0500"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setCodLoja(75);
        regra.setValorMinimo(new BigDecimal("1000.00"));
        regra.setMinInclusivo(true);
        regra.setValorMaximo(new BigDecimal("5000.00"));
        regra.setMaxInclusivo(true);
        regra.setStatus(StatusRegra.ATIVA);
        regraRepository.save(regra);

        Sale sale = new Sale();
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setValue(new BigDecimal("5000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));
        sale.setSaleChannel("ECOMMERCE");
        sale = saleRepository.save(sale);

        mockMvc.perform(post("/api/v1/comissoes/calcular/venda/" + sale.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCESSO")))
                .andExpect(jsonPath("$.taxaAplicada", is(0.05)))
                .andExpect(jsonPath("$.valorComissao", is(250.00)))
                .andExpect(jsonPath("$.origemTaxa", is("REGRA_NEGOCIO")));
    }

    @Test
    @DisplayName("2. Fallback Contratual: Venda fora da faixa deve retornar à taxa padrão de tb_basecomiss")
    void deveRetornarABaseQuandoValorVendaForaDaFaixa() throws Exception {
        Regra regra = new Regra(campanha, "Regra Faixa Ate 5K", "ECOMMERCE", new BigDecimal("0.0500"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setCodLoja(75);
        regra.setValorMaximo(new BigDecimal("5000.00"));
        regra.setMaxInclusivo(true);
        regra.setStatus(StatusRegra.ATIVA);
        regraRepository.save(regra);

        // Venda de R$ 6.000 (fora da faixa <= 5000)
        Sale sale = new Sale();
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setValue(new BigDecimal("6000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));
        sale.setSaleChannel("ECOMMERCE");
        sale = saleRepository.save(sale);

        mockMvc.perform(post("/api/v1/comissoes/calcular/venda/" + sale.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCESSO")))
                .andExpect(jsonPath("$.taxaAplicada", is(0.03)))
                .andExpect(jsonPath("$.valorComissao", is(180.00)))
                .andExpect(jsonPath("$.origemTaxa", is("BASE_COMISS")));
    }

    @Test
    @DisplayName("3. Ajuste sobre a Base: Deve calcular ACRESCIMO_PONTOS sobre a taxa de tb_basecomiss")
    void deveCalcularAcrescimoPontosSobreBase() throws Exception {
        Regra regra = new Regra(campanha, "Regra Bonus Base", "ECOMMERCE", null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setCodLoja(75);
        regra.setTipoOperacao(TipoOperacaoBase.ACRESCIMO_PONTOS);
        regra.setValorAjuste(new BigDecimal("0.0150")); // + 1.5%
        regra.setStatus(StatusRegra.ATIVA);
        regraRepository.save(regra);

        Sale sale = new Sale();
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setValue(new BigDecimal("4000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));
        sale.setSaleChannel("ECOMMERCE");
        sale = saleRepository.save(sale);

        // Base 3% + Ajuste 1.5% = 4.5% -> 4000 * 0.045 = 180.00
        mockMvc.perform(post("/api/v1/comissoes/calcular/venda/" + sale.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCESSO")))
                .andExpect(jsonPath("$.taxaAplicada", is(0.045)))
                .andExpect(jsonPath("$.valorComissao", is(180.00)))
                .andExpect(jsonPath("$.origemTaxa", is("REGRA_NEGOCIO")));
    }

    @Test
    @DisplayName("4. Bloqueio de Conflito: Regras concorrentes com mesmo score e mesma faixa devem gerar IMPEDIDO")
    void deveGerarImpedimentoEmEmpateDeEspecificidade() throws Exception {
        Regra regra1 = new Regra(campanha, "Regra Campanha Alpha", "ECOMMERCE", new BigDecimal("0.0400"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra1.setCodLoja(75);
        regra1.setStatus(StatusRegra.ATIVA);
        regraRepository.save(regra1);

        Regra regra2 = new Regra(campanha, "Regra Campanha Beta", "ECOMMERCE", new BigDecimal("0.0700"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra2.setCodLoja(75);
        regra2.setStatus(StatusRegra.ATIVA);
        regraRepository.save(regra2);

        Sale sale = new Sale();
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setValue(new BigDecimal("2000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));
        sale.setSaleChannel("ECOMMERCE");
        sale = saleRepository.save(sale);

        mockMvc.perform(post("/api/v1/comissoes/calcular/venda/" + sale.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IMPEDIDO")))
                .andExpect(jsonPath("$.motivoImpedimento", org.hamcrest.Matchers.containsString("Conflito de regras")));
    }

    @Test
    @DisplayName("5. Precedência Determinística: Regra individual de matrícula (peso 16) deve sobrepor regra de loja (peso 8)")
    void devePriorizarRegraIndividualDeMatriculaSobreRegraDeLoja() throws Exception {
        Regra regraLoja = new Regra(campanha, "Regra Loja 75", "ECOMMERCE", new BigDecimal("0.0350"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regraLoja.setCodLoja(75);
        regraLoja.setStatus(StatusRegra.ATIVA);
        regraRepository.save(regraLoja);

        Regra regraMatricula = new Regra(campanha, "Regra Top Vendedor", null, new BigDecimal("0.0900"),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regraMatricula.setMatricula("MATRIC-ADV-01");
        regraMatricula.setStatus(StatusRegra.ATIVA);
        regraRepository.save(regraMatricula);

        Sale sale = new Sale();
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setValue(new BigDecimal("3000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));
        sale.setSaleChannel("ECOMMERCE");
        sale = saleRepository.save(sale);

        mockMvc.perform(post("/api/v1/comissoes/calcular/venda/" + sale.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCESSO")))
                .andExpect(jsonPath("$.taxaAplicada", is(0.09)))
                .andExpect(jsonPath("$.valorComissao", is(270.00)))
                .andExpect(jsonPath("$.origemTaxa", is("REGRA_NEGOCIO")));
    }
}
