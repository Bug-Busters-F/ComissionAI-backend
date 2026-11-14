package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
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
import com.bugbusters.backend.service.client.AiServiceClient;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.support.PythonSandboxMockSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
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

    @Autowired
    private RestClient.Builder aiRestClientBuilder;

    @Autowired
    private AiServiceClient aiServiceClient;

    private PythonSandboxMockSupport sandboxMock;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        sandboxMock = PythonSandboxMockSupport.instalar(aiRestClientBuilder, aiServiceClient)
                .registrarExecucaoPadrao();

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

    @AfterEach
    void tearDown() {
        if (sandboxMock != null) {
            sandboxMock.restaurar();
        }
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
                null,
                new BigDecimal("50000.00"),
                null,
                true,
                "gestor.comissoes",
                "Orçamento aprovado para simulação"
        );

        MvcResult res = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private void aprovarProgramaPython(Long campanhaId) throws Exception {
        MvcResult resProg = mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId))
                .andExpect(status().isOk())
                .andReturn();
        String checksum = objectMapper.readTree(resProg.getResponse().getContentAsString()).get("checksumSha256").asText();
        AprovarProgramaPythonRequest aprovReq = new AprovarProgramaPythonRequest(
                "revisor.tecnico",
                checksum,
                "Programa homologado"
        );
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aprovReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve rejeitar simulação orquestrada caso não exista programa Python aprovado (400 Bad Request)")
    void deveRejeitarSimulacaoSemProgramaPythonAprovado() throws Exception {
        Long campanhaId = criarCampanhaComRegra();
        SimulacaoPythonRequest req = new SimulacaoPythonRequest("2026-07", null, 50, 0, false, false);

        mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve executar simulação no sandbox Python com programa homologado (BUG-106 / BACK-11)")
    void deveOrquestrarEPersistirSimulacaoPythonComSucesso() throws Exception {
        Long campanhaId = criarCampanhaComRegra();
        aprovarProgramaPython(campanhaId);

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
        assertThat(simPersistida.getSaidaPythonRaw()).contains("PYTHON_SANDBOX");
    }

    @Test
    @DisplayName("Deve refletir edição do código Python no resultado da simulação e falhar fechado se sandbox rejeitar")
    void deveRefletirCodigoPythonEditadoNaSimulacaoEFalharFechadoEmErroSandbox() throws Exception {
        Long campanhaId = criarCampanhaComRegra();
        String codigoEditado = """
                def calcular_comissao(venda, contexto):
                    taxa = 0.10
                    return venda.get("valor", 0.0) * taxa
                """;
        mockMvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.bugbusters.backend.dto.programapython.AtualizarProgramaPythonRequest(
                                codigoEditado, "revisor.python", "Sobrescreve taxa para 10% em Python"
                        ))))
                .andExpect(status().isOk());
        aprovarProgramaPython(campanhaId);

        SimulacaoPythonRequest req = new SimulacaoPythonRequest("2026-07", null, 50, 0, false, false);
        mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.executadoViaPython").value(true))
                .andExpect(jsonPath("$.cenario100.totalComissaoProposta").value(1000.00));

        assertThat(sandboxMock.getCodigosExecutados()).isNotEmpty();
        assertThat(sandboxMock.getCodigosExecutados().get(sandboxMock.getCodigosExecutados().size() - 1))
                .contains("taxa = 0.10");

        // Fail-closed quando sandbox rejeita código com erro de validação (422)
        sandboxMock.restaurar();
        PythonSandboxMockSupport mockErro = PythonSandboxMockSupport.instalar(aiRestClientBuilder, aiServiceClient)
                .registrarErroSandbox(HttpStatus.UNPROCESSABLE_ENTITY, "Import não permitido: os", List.of("Import não permitido: os"));
        try {
            mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("sandbox")));
        } finally {
            mockErro.restaurar();
            sandboxMock = PythonSandboxMockSupport.instalar(aiRestClientBuilder, aiServiceClient)
                    .registrarExecucaoPadrao();
        }
    }

    @Test
    @DisplayName("Deve permitir aceite formal da simulação orquestrada")
    void devePermitirAceiteFormalDaSimulacaoPython() throws Exception {
        Long campanhaId = criarCampanhaComRegra();
        aprovarProgramaPython(campanhaId);

        // 1. Executa simulação orquestrada
        SimulacaoPythonRequest req = new SimulacaoPythonRequest("2026-07", null, null, null, null, null);
        MvcResult simRes = mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode simJson = objectMapper.readTree(simRes.getResponse().getContentAsString());
        UUID simId = UUID.fromString(simJson.get("id").asText());

        // 2. Aceita a simulação
        AceiteSimulacaoRequest aceiteReq = new AceiteSimulacaoRequest("gestor.vendas@empresa.com", "Simulação orçamentária aprovada");
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
    @DisplayName("Deve atualizar a esteira e registrar histórico de transição ao simular")
    void deveAtualizarEsteiraERegistrarHistorico() throws Exception {
        Long campanhaId = criarCampanhaComRegra();
        aprovarProgramaPython(campanhaId);

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
