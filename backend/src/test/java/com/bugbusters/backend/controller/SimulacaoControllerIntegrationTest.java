package com.bugbusters.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
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
class SimulacaoControllerIntegrationTest {

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
    private Sale sale1;
    private Sale sale2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

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
        position.setCode(200);
        position.setDescription("VENDEDOR BALCAO");
        position = positionRepository.save(position);

        registration = new Registration();
        registration.setRegistration("MATRIC-SIM-01");
        registration.setStore(store);
        registration.setPosition(position);
        registration.setAdmissDate(LocalDate.of(2025, 1, 1));
        registration = registrationRepository.save(registration);

        // Taxa base contratual de 2.5% (0.0250)
        BaseComiss baseComiss = new BaseComiss(brand, position, new BigDecimal("0.0250"));
        baseComiss.setReferenceMonth(LocalDate.of(2026, 9, 1));
        baseComissRepository.save(baseComiss);

        // Venda 1: R$ 5.000,00 no canal ECOMMERCE em 2026-09-10
        sale1 = new Sale();
        sale1.setValue(new BigDecimal("5000.00"));
        sale1.setSaleDate(LocalDate.of(2026, 9, 10));
        sale1.setSaleChannel("ECOMMERCE");
        sale1.setBrand(brand);
        sale1.setStore(store);
        sale1.setRegistration(registration);
        sale1 = saleRepository.save(sale1);

        // Venda 2: R$ 1.500,00 no canal ECOMMERCE em 2026-09-20
        sale2 = new Sale();
        sale2.setValue(new BigDecimal("1500.00"));
        sale2.setSaleDate(LocalDate.of(2026, 9, 20));
        sale2.setSaleChannel("ECOMMERCE");
        sale2.setBrand(brand);
        sale2.setStore(store);
        sale2.setRegistration(registration);
        sale2 = saleRepository.save(sale2);

        // Campanha existente no banco: futura (15 dias em nov/2026)
        campanha = new Campanha("Campanha Novembro E-commerce", "Texto da campanha",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15));
        campanha.setEstado(EstadoCampanha.DRAFT);
        campanha = campanhaRepository.save(campanha);

        // Regra vinculada: canal ECOMMERCE, taxa 5% (0.0500)
        Regra regraCampanha = new Regra(
                campanha,
                "Regra E-commerce Novembro",
                "ECOMMERCE",
                new BigDecimal("0.0500"),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 15)
        );
        regraCampanha.setCodMarca(10);
        regraCampanha.setStatus(StatusRegra.DRAFT);
        regraRepository.save(regraCampanha);

        // Outra regra ativa independente de OUTRA campanha (deve ser IGNORADA no comparativo)
        Campanha outraCampanha = new Campanha("Outra Campanha Producao", "Outra",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        outraCampanha.setEstado(EstadoCampanha.ATIVA);
        outraCampanha = campanhaRepository.save(outraCampanha);

        Regra regraOutra = new Regra(
                outraCampanha,
                "Regra Ativa Outra Campanha",
                "ECOMMERCE",
                new BigDecimal("0.1000"), // 10%
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30)
        );
        regraOutra.setStatus(StatusRegra.ATIVA);
        regraRepository.save(regraOutra);
    }

    @Test
    @DisplayName("[BACK-6] POST /api/v1/simulacoes/cenarios: executa simulação em sandbox sem persistir comissões reais")
    void deveExecutarSimulacaoEmSandboxComSucesso() throws Exception {
        long contagemResultadosAntes = resultadoCalculoRepository.count();
        long contagemLogsAntes = logCalculoRepository.count();

        String payload = String.format("""
                {
                    "competencia": "2026-09",
                    "campanhaId": %d,
                    "limite": 100,
                    "pagina": 0
                }
                """, campanha.getId());

        mockMvc.perform(post("/api/v1/simulacoes/cenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia").value("2026-09"))
                .andExpect(jsonPath("$.totalVendasRecortadas").value(2))
                .andExpect(jsonPath("$.valorTotalRecortado").value(6500.00)) // 5000 + 1500
                .andExpect(jsonPath("$.diasInclusivos").value(15))
                .andExpect(jsonPath("$.fatorProjecao").value(0.500000))
                // Cenário 100% Proposta vs Base Contratual (2.5%)
                // Base: 6500 * 2.5% = 162.50 (ignora a regra de 10% da outra campanha ativa)
                // Proposta: 6500 * 5% = 325.00
                // Delta: 325.00 - 162.50 = 162.50
                .andExpect(jsonPath("$.cenario100.totalComissaoBase").value(162.50))
                .andExpect(jsonPath("$.cenario100.totalComissaoProposta").value(325.00))
                .andExpect(jsonPath("$.cenario100.deltaComissao").value(162.50))
                .andExpect(jsonPath("$.cenario100.deltaPercentual").value(100.00))
                // Projeção temporal: 325.00 * 0.50 = 162.50
                .andExpect(jsonPath("$.cenario100.comissaoProjetada").value(162.50))
                .andExpect(jsonPath("$.cenario100.deltaProjetado").value(81.25))
                // Cenário 80%: volume 6500 * 0.80 = 5200.00, comissao proposta 5200 * 5% = 260.00
                .andExpect(jsonPath("$.cenario80.totalVendas").value(5200.00))
                .andExpect(jsonPath("$.cenario80.totalComissaoProposta").value(260.00))
                // Cenário 120%: volume 6500 * 1.20 = 7800.00, comissao proposta 7800 * 5% = 390.00
                .andExpect(jsonPath("$.cenario120.totalVendas").value(7800.00))
                .andExpect(jsonPath("$.cenario120.totalComissaoProposta").value(390.00))
                // Cenário 150%: volume 6500 * 1.50 = 9750.00, comissao proposta 9750 * 5% = 487.50
                .andExpect(jsonPath("$.cenario150.totalVendas").value(9750.00))
                .andExpect(jsonPath("$.cenario150.totalComissaoProposta").value(487.50))
                .andExpect(jsonPath("$.totalVendasPaginadas").value(2));

        // Assegura NÃO persistência de comissões reais (Sandbox puro)
        assertThat(resultadoCalculoRepository.count()).isEqualTo(contagemResultadosAntes);
        assertThat(logCalculoRepository.count()).isEqualTo(contagemLogsAntes);

        // Assegura preservação das datas históricas das vendas no banco
        Sale venda1Recarregada = saleRepository.findById(sale1.getId()).orElseThrow();
        assertThat(venda1Recarregada.getSaleDate()).isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    @DisplayName("[BACK-6] POST /api/v1/referencias/simular-cenarios: compatibilidade via ReferenciaController")
    void deveExecutarSimulacaoPeloEndpointDeReferencias() throws Exception {
        String payload = String.format("""
                {
                    "competencia": "2026-09",
                    "campanhaId": %d
                }
                """, campanha.getId());

        mockMvc.perform(post("/api/v1/referencias/simular-cenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia").value("2026-09"))
                .andExpect(jsonPath("$.totalVendasRecortadas").value(2))
                .andExpect(jsonPath("$.cenario100.totalComissaoBase").value(162.50))
                .andExpect(jsonPath("$.cenario100.totalComissaoProposta").value(325.00));
    }
}
