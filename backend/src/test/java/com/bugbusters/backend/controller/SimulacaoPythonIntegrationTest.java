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

    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private org.springframework.security.web.FilterChainProxy securityFilterChain;
    @Autowired private com.bugbusters.backend.auth.UsuarioRepository usuarios;
    @Autowired private com.bugbusters.backend.auth.AuthService auth;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder encoder;

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
    @Test
    void preservaAnaliseAposConsultaERetomaSolicitacaoSemDuplicar() throws Exception {
        Long id=criarCampanhaComRegra();aprovarProgramaPython(id);
        UUID solicitacao=UUID.randomUUID();
        var req=new SimulacaoPythonRequest("2026-07",null,50,0,false,false,false,solicitacao);
        String body=objectMapper.writeValueAsString(req);
        var res=mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python",id).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.analise.statusSugestoes").value("NAO_SOLICITADA"))
            .andExpect(jsonPath("$.analise.explicacoes.length()").value(4)).andReturn();
        String simId=objectMapper.readTree(res.getResponse().getContentAsString()).path("id").asText();
        entityManager.flush();entityManager.clear();
        mockMvc.perform(get("/api/v1/simulacoes/{id}",simId)).andExpect(status().isOk()).andExpect(jsonPath("$.analise.execucaoId").value(simId));
        mockMvc.perform(get("/api/v1/simulacoes/{id}/analise-python",simId)).andExpect(status().isOk()).andExpect(jsonPath("$.execucaoId").value(simId));
        long quantidade=simulacaoRepository.count();
        mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python",id).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(simId));
        assertThat(simulacaoRepository.count()).isEqualTo(quantidade);
        var outra=new SimulacaoPythonRequest("2026-08",null,50,0,false,false,false,solicitacao);
        mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python",id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(outra)))
            .andExpect(status().isConflict());
    }

    @Test
    void aplicaAlternativaSomenteNoRascunhoEExigeNovaRevisaoESimulacao() throws Exception {
        Long id=criarCampanhaComRegra();
        Campanha c=campanhaRepository.findById(id).orElseThrow();
        c.setOrcamento(new BigDecimal("450"));c.getVersaoProposta().setOrcamento(new BigDecimal("450"));
        campanhaRepository.saveAndFlush(c);aprovarProgramaPython(id);
        sandboxMock.registrarSugestoes(payload -> {
            String regraId=payload.path("propostas").get(0).path("idLocal").asText();
            java.util.List<java.util.Map<String,Object>> resultados=new java.util.ArrayList<>(), alternativas=new java.util.ArrayList<>();
            int idx=0;
            for(String fator:List.of("0.8","1","1.2","1.5")) {
                BigDecimal f=new BigDecimal(fator);String taxa=List.of("0.05","0.04","0.035","0.03").get(idx++);
                resultados.add(java.util.Map.of("multiplicadorVendas",fator,"totalComissao",new BigDecimal("500").multiply(f),"custoContratual",new BigDecimal("300").multiply(f)));
                alternativas.add(java.util.Map.of("multiplicadorVendas",fator,"status",fator.equals("0.8")?"NAO_NECESSARIA":"ENQUADRADA",
                    "custoPropostoRecalculado",new BigDecimal("10000").multiply(f).multiply(new BigDecimal(taxa)),
                    "alteracoes",List.of(java.util.Map.of("idLocal",regraId,"campo","taxa","antes","0.05","depois",taxa))));
            } return java.util.Map.of("resultados",resultados,"calibracoes",alternativas);
        });
        var res=mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python",id).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SimulacaoPythonRequest("2026-07",null,50,0,false,false,true))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.analise.statusSugestoes").value("OK"))
            .andExpect(jsonPath("$.analise.calibracoes[1].status").value("ENQUADRADA")).andReturn();
        JsonNode resultado=objectMapper.readTree(res.getResponse().getContentAsString());
        UUID sid=UUID.fromString(resultado.path("id").asText());
        var sim=simulacaoRepository.findById(sid).orElseThrow();
        assertThat(sim.getCampanhaVersao().getProgramaPythonAprovado()).isNotNull();
        var req=new com.bugbusters.backend.dto.simulacao.AplicarCalibracaoRequest("100%",c.getVersaoControle(),resultado.path("checksumPython").asText());
        var aplicacao=mockMvc.perform(post("/api/v1/simulacoes/{id}/calibracoes/aplicar",sid).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andReturn();
        assertThat(aplicacao.getResponse().getStatus()).withFailMessage(aplicacao.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(objectMapper.readTree(aplicacao.getResponse().getContentAsString()).path("statusAprovacao").asText()).isEqualTo("PENDENTE_REVISAO");
        assertThat(sim.getStatusAceite()).isEqualTo(StatusAceiteSimulacao.INVALIDADA);
        assertThat(c.getVersaoProposta().getProgramaPythonAprovado()).isNull();
        assertThat(c.getVersaoProposta().getUltimaEtapa()).isEqualTo(2);
        mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python",id).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SimulacaoPythonRequest("2026-07",null,50,0,false,false))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/simulacoes/{id}/calibracoes/aplicar",sid).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }

    @Test
    void contratoRealComIaPr18QuandoConfigurado() throws Exception {
        String url=System.getProperty("back11.ia-url");
        org.junit.jupiter.api.Assumptions.assumeTrue(url!=null,"Teste opt-in: requer instância isolada da IA do PR #18");
        Long id=criarCampanhaComRegra(); Campanha c=campanhaRepository.findById(id).orElseThrow();
        c.setOrcamento(new BigDecimal("450"));c.getVersaoProposta().setOrcamento(new BigDecimal("450"));
        c.setDataInicio(LocalDate.of(2026,7,1));c.setDataFim(LocalDate.of(2026,7,31));
        c.getVersaoProposta().setDataInicio(c.getDataInicio());c.getVersaoProposta().setDataFim(c.getDataFim());
        campanhaRepository.saveAndFlush(c);aprovarProgramaPython(id);
        sandboxMock.restaurar();
        RestClient original=aiServiceClient.getAiRestClient();
        Object chave=org.springframework.test.util.ReflectionTestUtils.getField(aiServiceClient,"authKey");
        try {
            aiServiceClient.setAiRestClient(RestClient.builder().baseUrl(url).requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory()).build());
            org.springframework.test.util.ReflectionTestUtils.setField(aiServiceClient,"authKey","back11-integration");
            var res=mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python",id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SimulacaoPythonRequest("2026-07",null,50,0,false,false,true))))
                .andReturn();
            assertThat(res.getResponse().getStatus()).withFailMessage(res.getResponse().getContentAsString()).isEqualTo(201);
            var json=objectMapper.readTree(res.getResponse().getContentAsString());
            assertThat(json.path("analise").path("statusSugestoes").asText()).withFailMessage(json.toString()).isEqualTo("OK");
            assertThat(json.path("analise").path("calibracoes").get(1).path("status").asText()).isEqualTo("ENQUADRADA");
            assertThat(json.path("analise").path("calibracoes").get(1).path("custoProjetadoRecalculado").decimalValue()).isLessThanOrEqualTo(new BigDecimal("450"));
        } finally {aiServiceClient.setAiRestClient(original);org.springframework.test.util.ReflectionTestUtils.setField(aiServiceClient,"authKey",chave);}
    }

    @Test
    void somenteNegociosEAdministradorPodemAplicarCalibracao() throws Exception {
        var mvc=MockMvcBuilders.webAppContextSetup(webApplicationContext).addFilters(securityFilterChain).build();
        UUID inexistente=UUID.randomUUID();String body="{\"cenario\":\"100%\",\"revisaoEsperada\":0,\"assinaturaProgramaEsperada\":\"hash\"}";
        mvc.perform(post("/api/v1/simulacoes/{id}/calibracoes/aplicar",inexistente).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        for(var papel:com.bugbusters.backend.auth.PapelUsuario.values()) {
            var u=new com.bugbusters.backend.auth.Usuario();u.setNome("Teste calibração");u.setEmail("calibracao-"+UUID.randomUUID()+"@teste.local");u.setPapel(papel);u.setSenhaHash(encoder.encode("Senha@123"));usuarios.saveAndFlush(u);
            String token=auth.login(new com.bugbusters.backend.auth.dto.LoginRequest(u.getEmail(),"Senha@123")).token();
            boolean permitido=papel==com.bugbusters.backend.auth.PapelUsuario.ADMINISTRADOR || papel==com.bugbusters.backend.auth.PapelUsuario.ANALISTA_NEGOCIOS;
            mvc.perform(post("/api/v1/simulacoes/{id}/calibracoes/aplicar",inexistente).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(permitido?404:403));
            mvc.perform(get("/api/v1/simulacoes/{id}/analise-python",inexistente).header("Authorization","Bearer "+token))
                .andExpect(status().isNotFound());
        }
    }

}
