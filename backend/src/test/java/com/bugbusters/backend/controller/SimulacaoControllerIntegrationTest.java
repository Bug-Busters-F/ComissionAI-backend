package com.bugbusters.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosResponse;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.SimulacaoCenariosService;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimulacaoControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private SimulacaoCenariosService service;

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
    private SaleRepository saleRepository;

    @Autowired
    private com.bugbusters.backend.repository.SimulacaoVendaRepository simulacaoVendaRepository;

    @Autowired
    private com.bugbusters.backend.repository.SimulacaoRepository simulacaoRepository;



    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;
    private Campanha campanha;
    private Sale sale;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        brand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("PRETO");
            return brandRepository.save(b);
        });

        store = storeRepository.findByCode(75).orElseGet(() -> {
            Store s = new Store();
            s.setCode(75);
            s.setDescription("LOJA CENTRO");
            return storeRepository.save(s);
        });

        position = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR");
            return positionRepository.save(p);
        });

        registration = registrationRepository.findByRegistration("MATRIC-CTR").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-CTR");
            r.setPosition(position);
            r.setStore(store);
            r.setAdmissDate(LocalDate.of(2020, 1, 1));
            return registrationRepository.save(r);
        });

        LocalDate mesRef = LocalDate.of(2026, 7, 1);
        baseComissRepository.findByBrandIdAndPositionIdAndReferenceMonth(brand.getId(), position.getId(), mesRef)
                .orElseGet(() -> {
                    BaseComiss bc = new BaseComiss(brand, position, new BigDecimal("0.0300"));
                    bc.setReferenceMonth(mesRef);
                    return baseComissRepository.save(bc);
                });

        Campanha c = new Campanha();
        c.setTitulo("Campanha Black Friday Controller");
        c.setTextoOriginal("Campanha teste");
        c.setDataInicio(LocalDate.of(2026, 11, 1));
        c.setDataFim(LocalDate.of(2026, 11, 15));
        campanha = campanhaRepository.save(c);

        sale = new Sale();
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setSaleChannel("ECOMMERCE");
        sale.setSaleDate(LocalDate.of(2026, 7, 12));
        sale.setValue(new BigDecimal("6000.00"));
        saleRepository.save(sale);
    }

    private RegraItemRequest criarRegra(String nome, BigDecimal taxa) {
        return new RegraItemRequest(
                "bloco-1", "regra", nome, "ECOMMERCE",
                10, "PRETO", null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, taxa,
                null, null, null, taxa,
                null, null, null, true, null,
                null, null
        );
    }

    private SimulacaoCenariosRequest criarRequest(Long campanhaId, List<RegraItemRequest> regras) {
        return new SimulacaoCenariosRequest(
                "2026-07",
                campanhaId,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 15),
                regras,
                50,
                0,
                false,
                false
        );
    }

    @Test
    @DisplayName("POST /api/v1/simulacoes deve executar, persistir e retornar status 201 Created")
    void deveExecutarEPersistirSimulacaoViaPost() throws Exception {
        RegraItemRequest regra = criarRegra("Regra 5%", new BigDecimal("0.0500"));
        SimulacaoCenariosRequest request = criarRequest(campanha.getId(), List.of(regra));

        mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.protocolo").isNotEmpty())
                .andExpect(jsonPath("$.competencia").value("2026-07"))
                .andExpect(jsonPath("$.totalVendasRecortadas").value(1))
                .andExpect(jsonPath("$.cenario100.totalVendas").value(6000.0))
                .andExpect(jsonPath("$.cenario100.totalComissaoProposta").value(300.0))
                .andExpect(jsonPath("$.cenario100.totalComissaoBase").value(180.0))
                .andExpect(jsonPath("$.cenario100.deltaComissao").value(120.0))
                .andExpect(jsonPath("$.memoriaCalculoConsolidada").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/simulacoes/{id}/vendas deve retornar vendas paginadas com totais globais no cabeçalho")
    void deveRetornarVendasPaginadasComTotaisGlobais() throws Exception {
        RegraItemRequest regra = criarRegra("Regra 5%", new BigDecimal("0.0500"));
        SimulacaoCenariosRequest request = criarRequest(campanha.getId(), List.of(regra));

        SimulacaoCenariosResponse sim = service.simularEPersistir(request);

        mockMvc.perform(get("/api/v1/simulacoes/" + sim.id() + "/vendas")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulacaoId").value(sim.id().toString()))
                .andExpect(jsonPath("$.protocolo").value(sim.protocolo().toString()))
                .andExpect(jsonPath("$.cenario100.totalVendas").value(6000.0))
                .andExpect(jsonPath("$.cenario100.totalComissaoProposta").value(300.0))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.vendas[0].id").value(sale.getId().toString()))
                .andExpect(jsonPath("$.vendas[0].memoriaCalculo").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/simulacoes/{id}/vendas/{idVenda} deve retornar detalhe e memória da venda")
    void deveRetornarDetalheEMemoriaVenda() throws Exception {
        RegraItemRequest regra = criarRegra("Regra 5%", new BigDecimal("0.0500"));
        SimulacaoCenariosRequest request = criarRequest(campanha.getId(), List.of(regra));

        SimulacaoCenariosResponse sim = service.simularEPersistir(request);

        mockMvc.perform(get("/api/v1/simulacoes/" + sim.id() + "/vendas/" + sale.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sale.getId().toString()))
                .andExpect(jsonPath("$.valorVendaOriginal").value(6000.0))
                .andExpect(jsonPath("$.taxaBaseContratual").value(0.03))
                .andExpect(jsonPath("$.valorComissaoBase").value(180.0))
                .andExpect(jsonPath("$.cenario100.valorComissao").value(300.0))
                .andExpect(jsonPath("$.memoriaCalculo").isNotEmpty());
    }
}
