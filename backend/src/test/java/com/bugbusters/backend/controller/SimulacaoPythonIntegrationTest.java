package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.simulacao.AceiteSimulacaoRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoPythonRequest;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Simulacao;
import com.bugbusters.backend.model.StatusAceiteSimulacao;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.SimulacaoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.StoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimulacaoPythonIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
    private SaleRepository saleRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private SimulacaoRepository simulacaoRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        Brand brand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("PRETO");
            return brandRepository.save(b);
        });

        com.bugbusters.backend.store.Store store = storeRepository.findByCode(75).orElseGet(() -> {
            com.bugbusters.backend.store.Store s = new com.bugbusters.backend.store.Store();
            s.setCode(75);
            s.setDescription("LOJA TESTE");
            return storeRepository.save(s);
        });

        Position position = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR");
            return positionRepository.save(p);
        });

        Registration registration = registrationRepository.findByRegistration("MATRIC-SIM-PY").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-SIM-PY");
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

        Sale sale = new Sale();
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setSaleChannel("ECOMMERCE");
        sale.setSaleDate(LocalDate.of(2026, 7, 15));
        sale.setValue(new BigDecimal("10000.00"));
        saleRepository.save(sale);
    }

    private Long criarCampanhaComRegra() throws Exception {
        RegraItemRequest regra1 = new RegraItemRequest(
                "bloco-1",
                "Comissão de 5% no canal ecommerce",
                "Regra E-commerce 5%",
                "ECOMMERCE",
                10, "PRETO", null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA,
                null, null, null, null,
                BigDecimal.valueOf(0.05),
                "5% sobre vendas",
                "taxa = 0.05",
                null,
                true,
                null,
                null,
                null
        );

        CampanhaRequest request = new CampanhaRequest(
                "Campanha Teste Simulação Python",
                "Texto base da campanha de teste de simulação com python",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                EstadoCampanha.DRAFT,
                List.of(regra1),
                null
        );

        MvcResult res = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    @Test
    @DisplayName("Deve orquestrar e persistir simulação executada via programa Python com sucesso (BUG-106 / BACK-11)")
    void deveOrquestrarEPersistirSimulacaoPythonComSucesso() throws Exception {
        Long campanhaId = criarCampanhaComRegra();

        SimulacaoPythonRequest req = new SimulacaoPythonRequest("2026-07", null, 50, 0, false, false);

        MvcResult res = mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.protocolo").isNotEmpty())
                .andExpect(jsonPath("$.competencia").value("2026-07"))
                .andExpect(jsonPath("$.executadoViaPython").value(true))
                .andExpect(jsonPath("$.versaoProgramaPython").value(1))
                .andExpect(jsonPath("$.checksumPython").isNotEmpty())
                .andExpect(jsonPath("$.totalVendasRecortadas").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.cenario100.totalComissaoProposta").isNotEmpty())
                .andExpect(jsonPath("$.cenario80.totalComissaoProposta").isNotEmpty())
                .andExpect(jsonPath("$.cenario120.totalComissaoProposta").isNotEmpty())
                .andExpect(jsonPath("$.cenario150.totalComissaoProposta").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        UUID simulacaoId = UUID.fromString(json.get("id").asText());

        // Validação da persistência relacional em tb_simulacao
        Simulacao simPersistida = simulacaoRepository.findById(simulacaoId).orElseThrow();
        assertThat(simPersistida.isExecutadoViaPython()).isTrue();
        assertThat(simPersistida.getVersaoProgramaPython()).isEqualTo(1);
        assertThat(simPersistida.getChecksumPython()).isNotBlank();
        assertThat(simPersistida.getCodigoPythonExecutado()).contains("def calcular_comissao");
    }

    @Test
    @DisplayName("Deve permitir aceite formal da simulação orquestrada via Python")
    void devePermitirAceiteFormalDaSimulacaoPython() throws Exception {
        Long campanhaId = criarCampanhaComRegra();

        // 1. Executa simulação Python
        SimulacaoPythonRequest req = new SimulacaoPythonRequest("2026-07", null, null, null, null, null);
        MvcResult simRes = mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode simJson = objectMapper.readTree(simRes.getResponse().getContentAsString());
        UUID simId = UUID.fromString(simJson.get("id").asText());

        // 2. Aceita a simulação
        AceiteSimulacaoRequest aceiteReq = new AceiteSimulacaoRequest("gestor.vendas@empresa.com", "Simulação orçamentária aprovada em Python");
        mockMvc.perform(post("/api/v1/simulacoes/{id}/aceite", simId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceiteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAceite").value("ACEITA"))
                .andExpect(jsonPath("$.aceitoPor").value("gestor.vendas@empresa.com"));

        // 3. Verifica que a campanha vinculou o aceite
        Campanha c = campanhaRepository.findById(campanhaId).orElseThrow();
        assertThat(c.getSimulacaoAceita()).isNotNull();
        assertThat(c.getSimulacaoAceita().getId()).isEqualTo(simId);
        assertThat(c.getSimulacaoAceita().isExecutadoViaPython()).isTrue();
    }

    @Test
    @DisplayName("Deve atualizar a esteira e registrar histórico de transição ao simular em Python")
    void deveAtualizarEsteiraERegistrarHistorico() throws Exception {
        Long campanhaId = criarCampanhaComRegra();

        SimulacaoPythonRequest req = new SimulacaoPythonRequest("2026-07", null, null, null, null, null);
        mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Verifica que a esteira avançou para etapa 3
        mockMvc.perform(get("/api/v1/campanhas/{id}/esteira", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(3))
                .andExpect(jsonPath("$.estadoEsteira").value("EM_SIMULACAO"));

        // Verifica o registro no histórico
        mockMvc.perform(get("/api/v1/campanhas/{id}/esteira/historico", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].responsavel").value("orquestrador-python"))
                .andExpect(jsonPath("$[0].motivo", containsString("programa Python")));
    }
}
