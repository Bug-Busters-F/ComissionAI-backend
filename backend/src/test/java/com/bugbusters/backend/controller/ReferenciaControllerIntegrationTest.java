package com.bugbusters.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
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
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReferenciaControllerIntegrationTest {

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

    private MockMvc mockMvc;
    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;
    private Campanha campanha;

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
        registration.setRegistration("MATRIC-REF-01");
        registration.setStore(store);
        registration.setPosition(position);
        registration.setAdmissDate(LocalDate.of(2025, 1, 1));
        registration = registrationRepository.save(registration);

        BaseComiss baseComiss = new BaseComiss(brand, position, new BigDecimal("0.0200"));
        baseComiss.setReferenceMonth(LocalDate.of(2026, 9, 1));
        baseComissRepository.save(baseComiss);

        Sale sale = new Sale();
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setValue(new BigDecimal("5000.00"));
        sale.setSaleDate(LocalDate.of(2026, 9, 15));
        sale.setSaleChannel("ECOMMERCE");
        saleRepository.save(sale);

        campanha = new Campanha("Campanha Referencia Test", "Texto teste", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        campanha.setEstado(EstadoCampanha.ATIVA);
        campanha = campanhaRepository.save(campanha);

        Regra regra = new Regra(campanha, "Regra Loja 75", "ECOMMERCE", new BigDecimal("0.0500"), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        regra.setCodLoja(75);
        regra = regraRepository.save(regra);
        campanha.getRegras().add(regra);
    }

    @Test
    @DisplayName("Deve listar competências disponíveis via GET /api/v1/referencias/competencias")
    void deveListarCompetencias() throws Exception {
        mockMvc.perform(get("/api/v1/referencias/competencias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].competencia", is("2026-09")))
                .andExpect(jsonPath("$[0].totalVendas", is(1)))
                .andExpect(jsonPath("$[0].valorTotalVendas", is(5000.00)))
                .andExpect(jsonPath("$[0].statusReferencia", is("DISPONIVEL")));
    }

    @Test
    @DisplayName("Deve detalhar competência via GET /api/v1/referencias/competencias/{competencia}")
    void deveDetalharCompetencia() throws Exception {
        mockMvc.perform(get("/api/v1/referencias/competencias/2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia", is("2026-09")))
                .andExpect(jsonPath("$.totalVendas", is(1)))
                .andExpect(jsonPath("$.totalVendasComVinculoValido", is(1)))
                .andExpect(jsonPath("$.possuiBaseComiss", is(true)));
    }

    @Test
    @DisplayName("Deve recortar união de públicos via POST com campanhaId existente")
    void deveRecortarPublicoPorCampanhaId() throws Exception {
        String payload = """
                {
                    "competencia": "2026-09",
                    "campanhaId": %d
                }
                """.formatted(campanha.getId());

        mockMvc.perform(post("/api/v1/referencias/recortar-publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia", is("2026-09")))
                .andExpect(jsonPath("$.totalVendasCompetencia", is(1)))
                .andExpect(jsonPath("$.totalVendasRecortadas", is(1)))
                .andExpect(jsonPath("$.totalVendasAptas", is(1)))
                .andExpect(jsonPath("$.vendas", hasSize(1)))
                .andExpect(jsonPath("$.vendas[0].matricula", is("MATRIC-REF-01")))
                .andExpect(jsonPath("$.vendas[0].codLoja", is(75)));
    }

    @Test
    @DisplayName("Deve recortar união de públicos via POST com regras avulsas em rascunho")
    void deveRecortarPublicoComRegrasAvulsas() throws Exception {
        String payload = """
                {
                    "competencia": "2026-09",
                    "regras": [
                        {
                            "blocoId": "bloco-1",
                            "nome": "Regra Avulsa E-commerce",
                            "canal": "ECOMMERCE",
                            "codLoja": 75,
                            "tipoOperacao": "DEFINIR_TAXA",
                            "taxa": 0.0500
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/referencias/recortar-publico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVendasRecortadas", is(1)))
                .andExpect(jsonPath("$.vendas[0].regraVencedoraNome", is("Regra Avulsa E-commerce")));
    }

    @Test
    @DisplayName("Deve recortar união de públicos via GET com Query String")
    void deveRecortarPublicoViaGet() throws Exception {
        mockMvc.perform(get("/api/v1/referencias/recortar-publico")
                        .param("competencia", "2026-09")
                        .param("campanhaId", String.valueOf(campanha.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia", is("2026-09")))
                .andExpect(jsonPath("$.totalVendasRecortadas", is(1)));
    }
}
