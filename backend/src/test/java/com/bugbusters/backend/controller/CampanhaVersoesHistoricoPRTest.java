package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.AtivarCampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.dto.simulacao.AceiteSimulacaoRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.support.CampaignDataCleanup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bateria completa de testes do PR BACK-10:
 * Histórico, autoria e comparação entre versões.
 */
@SpringBootTest
@ActiveProfiles("test")
class CampanhaVersoesHistoricoPRTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    private com.bugbusters.backend.repository.CampanhaRepository campanhaRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        CampaignDataCleanup.clear(jdbcTemplate);

        brand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("MARCA_PR_TEST");
            return brandRepository.save(b);
        });

        store = storeRepository.findByCode(75).orElseGet(() -> {
            Store s = new Store();
            s.setCode(75);
            s.setDescription("LOJA_PR_75");
            return storeRepository.save(s);
        });

        position = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR_PR_100");
            return positionRepository.save(p);
        });

        registration = registrationRepository.findByRegistration("MAT-PR-001").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MAT-PR-001");
            r.setStore(store);
            r.setPosition(position);
            r.setAdmissDate(LocalDate.of(2026, 1, 1));
            return registrationRepository.save(r);
        });

        if (baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(10, 100).isEmpty()) {
            BaseComiss bc = new BaseComiss(brand, position, new BigDecimal("0.1000"));
            baseComissRepository.save(bc);
        }

        Sale sale = new Sale();
        sale.setId(UUID.randomUUID());
        sale.setSaleDate(LocalDate.of(2026, 6, 10));
        sale.setValue(new BigDecimal("1000.00"));
        sale.setSaleChannel("ECOMMERCE");
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        saleRepository.save(sale);
    }

    @AfterEach
    void tearDown() {
        CampaignDataCleanup.clear(jdbcTemplate);
    }

    @Test
    @DisplayName("Executar a bateria completa do PR BACK-10: Passos 1 a 5")
    void testarBateriaCompletaPRBack10() throws Exception {
        // =========================================================================
        // PASSO 1: Gerar duas versões publicadas
        // 1. Publique a versão 1 de uma campanha com uma regra de comissão de 5%.
        // =========================================================================
        RegraItemRequest regraV1 = new RegraItemRequest(
                "bloco-1", "Trecho 5% vendas", "Regra 5% E-commerce", "ECOMMERCE",
                10, null, 75, 100, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0500"),
                "Comissão de 5% sobre e-commerce",
                "taxa = 0.05",
                List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest criarReq = new CampanhaRequest(
                "Campanha Teste PR BACK-10", "Campanha teste de versões",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                EstadoCampanha.DRAFT, List.of(regraV1)
        );

        MvcResult postCampanhaResult = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criarReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        JsonNode campanhaJson = objectMapper.readTree(postCampanhaResult.getResponse().getContentAsString());
        long campanhaId = campanhaJson.get("id").asLong();

        // Simular proposta v1
        SimulacaoCenariosRequest simReqV1 = new SimulacaoCenariosRequest(
                "2026-06", campanhaId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                null, 50, 0, false, false
        );
        MvcResult simV1Result = mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simReqV1)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID simV1Id = UUID.fromString(objectMapper.readTree(simV1Result.getResponse().getContentAsString()).get("id").asText());

        // Aceitar simulação v1
        AceiteSimulacaoRequest aceiteV1 = new AceiteSimulacaoRequest("gestor.v1@empresa.com", "Aprovado v1");
        mockMvc.perform(post("/api/v1/simulacoes/" + simV1Id + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceiteV1)))
                .andExpect(status().isOk());

        // Publicar versão 1
        mockMvc.perform(post("/api/v1/campanhas/" + campanhaId + "/ativar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.versaoVigente").value(1));

        // 2. Edite a campanha, mantendo o mesmo blocoId da regra e alterando a taxa para 6%.
        RegraItemRequest regraV2 = new RegraItemRequest(
                "bloco-1", "Trecho 6% vendas", "Regra 6% E-commerce", "ECOMMERCE",
                10, null, 75, 100, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0600"),
                "Comissão revisada para 6%",
                "taxa = 0.06",
                List.of(), true, StatusRegra.DRAFT
        );
        CampanhaRequest editReqV2 = new CampanhaRequest(
                "Campanha Teste PR BACK-10 Revisada", "Campanha teste de versões",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                EstadoCampanha.DRAFT, List.of(regraV2)
        );

        long revisaoAtual = campanhaRepository.findById(campanhaId).get().getVersaoControle();
        mockMvc.perform(put("/api/v1/campanhas/" + campanhaId)
                        .header("X-Campanha-Revisao", revisaoAtual)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReqV2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoProposta").value(2))
                .andExpect(jsonPath("$.versaoVigente").value(1));

        // 3. Simule e publique a proposta como versão 2.
        SimulacaoCenariosRequest simReqV2 = new SimulacaoCenariosRequest(
                "2026-06", campanhaId, null, null, null, 50, 0, false, false
        );
        MvcResult simV2Result = mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simReqV2)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID simV2Id = UUID.fromString(objectMapper.readTree(simV2Result.getResponse().getContentAsString()).get("id").asText());

        AceiteSimulacaoRequest aceiteV2 = new AceiteSimulacaoRequest("diretor.v2@empresa.com", "Aprovado v2 com 6%");
        mockMvc.perform(post("/api/v1/simulacoes/" + simV2Id + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceiteV2)))
                .andExpect(status().isOk());

        long revisaoAtivar = campanhaRepository.findById(campanhaId).get().getVersaoControle();
        mockMvc.perform(post("/api/v1/campanhas/" + campanhaId + "/ativar")
                        .header("X-Campanha-Revisao", revisaoAtivar)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtivarCampanhaRequest(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.versaoVigente").value(2));

        // =========================================================================
        // PASSO 2: Consultar o histórico
        // GET /api/v1/campanhas/{campanhaId}/versoes
        // Resultado esperado: versões 2 e 1, com número anterior, responsável, data de publicação, ID da simulação aceita e protocolo.
        // =========================================================================
        MvcResult historicoResult = mockMvc.perform(get("/api/v1/campanhas/" + campanhaId + "/versoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // Versão 2 (mais recente primeiro)
                .andExpect(jsonPath("$[0].numero").value(2))
                .andExpect(jsonPath("$[0].versaoAnterior").value(1))
                .andExpect(jsonPath("$[0].responsavel").value("diretor.v2@empresa.com"))
                .andExpect(jsonPath("$[0].publicadoEm").isNotEmpty())
                .andExpect(jsonPath("$[0].simulacaoAceitaId").value(simV2Id.toString()))
                .andExpect(jsonPath("$[0].protocoloSimulacao").isNotEmpty())
                // Versão 1
                .andExpect(jsonPath("$[1].numero").value(1))
                .andExpect(jsonPath("$[1].versaoAnterior").value(nullValue()))
                .andExpect(jsonPath("$[1].responsavel").value("gestor.v1@empresa.com"))
                .andExpect(jsonPath("$[1].publicadoEm").isNotEmpty())
                .andExpect(jsonPath("$[1].simulacaoAceitaId").value(simV1Id.toString()))
                .andExpect(jsonPath("$[1].protocoloSimulacao").isNotEmpty())
                .andReturn();

        System.out.println("=== PASSO 2: HISTÓRICO ===");
        System.out.println(historicoResult.getResponse().getContentAsString());

        // =========================================================================
        // PASSO 3: Consultar versão imutável
        // GET /api/v1/campanhas/{campanhaId}/versoes/1
        // Resultado esperado: taxa de 5%, artefatos explicativos e Python que pertenciam à versão 1
        // =========================================================================
        MvcResult v1DetalheResult = mockMvc.perform(get("/api/v1/campanhas/" + campanhaId + "/versoes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.versaoAnterior").value(nullValue()))
                .andExpect(jsonPath("$.regras", hasSize(1)))
                .andExpect(jsonPath("$.regras[0].identificadorLogico").value("bloco-1"))
                .andExpect(jsonPath("$.regras[0].taxa").value(0.0500))
                .andExpect(jsonPath("$.regras[0].explicacao").value("Comissão de 5% sobre e-commerce"))
                .andExpect(jsonPath("$.regras[0].pythonEquivalente").value("taxa = 0.05"))
                .andReturn();

        System.out.println("=== PASSO 3: VERSÃO 1 IMUTÁVEL ===");
        System.out.println(v1DetalheResult.getResponse().getContentAsString());

        // GET /api/v1/campanhas/{campanhaId}/versoes/2
        // Resultado esperado: taxa de 6% e metadados da publicação da versão 2
        MvcResult v2DetalheResult = mockMvc.perform(get("/api/v1/campanhas/" + campanhaId + "/versoes/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(2))
                .andExpect(jsonPath("$.versaoAnterior").value(1))
                .andExpect(jsonPath("$.responsavel").value("diretor.v2@empresa.com"))
                .andExpect(jsonPath("$.regras", hasSize(1)))
                .andExpect(jsonPath("$.regras[0].identificadorLogico").value("bloco-1"))
                .andExpect(jsonPath("$.regras[0].taxa").value(0.0600))
                .andExpect(jsonPath("$.regras[0].explicacao").value("Comissão revisada para 6%"))
                .andExpect(jsonPath("$.regras[0].pythonEquivalente").value("taxa = 0.06"))
                .andReturn();

        System.out.println("=== PASSO 3: VERSÃO 2 ===");
        System.out.println(v2DetalheResult.getResponse().getContentAsString());

        // =========================================================================
        // PASSO 4: Comparar versões
        // GET /api/v1/campanhas/{campanhaId}/versoes/comparacao?versaoBase=1&versaoComparada=2
        // =========================================================================
        MvcResult comparacao1e2Result = mockMvc.perform(get("/api/v1/campanhas/" + campanhaId + "/versoes/comparacao")
                        .param("versaoBase", "1")
                        .param("versaoComparada", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoBase").value(1))
                .andExpect(jsonPath("$.versaoComparada").value(2))
                .andExpect(jsonPath("$.alteracoesRegras", hasSize(1)))
                .andExpect(jsonPath("$.alteracoesRegras[0].identificadorLogico").value("bloco-1"))
                .andExpect(jsonPath("$.alteracoesRegras[0].tipo").value("ALTERADA"))
                .andReturn();

        System.out.println("=== PASSO 4: COMPARAÇÃO 1 vs 2 ===");
        System.out.println(comparacao1e2Result.getResponse().getContentAsString());

        JsonNode compJson = objectMapper.readTree(comparacao1e2Result.getResponse().getContentAsString());
        JsonNode alteracoes = compJson.get("alteracoesRegras").get(0).get("alteracoes");
        boolean achouTaxa = false;
        for (JsonNode alt : alteracoes) {
            if ("taxa".equals(alt.get("campo").asText())) {
                achouTaxa = true;
                assertThat(alt.get("anterior").asDouble()).isEqualTo(0.0500);
                assertThat(alt.get("atual").asDouble()).isEqualTo(0.0600);
            }
        }
        assertThat(achouTaxa).isTrue();

        // =========================================================================
        // PASSO 5: Testar inclusão e remoção de regra
        // 1. Publique uma nova versão adicionando uma regra com novo blocoId (bloco-2).
        // =========================================================================
        RegraItemRequest regraV3Extra = new RegraItemRequest(
                "bloco-2", "Trecho 7% especial", "Regra 7% Bônus", "ECOMMERCE",
                10, null, 75, 100, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0700"),
                "Nova regra inclusa",
                "taxa = 0.07",
                List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest editReqV3 = new CampanhaRequest(
                "Campanha Teste PR BACK-10 V3", "Campanha teste de versões",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                EstadoCampanha.DRAFT, List.of(regraV2, regraV3Extra)
        );

        long revisaoV3 = campanhaRepository.findById(campanhaId).get().getVersaoControle();
        mockMvc.perform(put("/api/v1/campanhas/" + campanhaId)
                        .header("X-Campanha-Revisao", revisaoV3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReqV3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoProposta").value(3));

        // Simular e aceitar v3
        MvcResult simV3Result = mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SimulacaoCenariosRequest(
                                "2026-06", campanhaId, null, null, null, 50, 0, false, false))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID simV3Id = UUID.fromString(objectMapper.readTree(simV3Result.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/simulacoes/" + simV3Id + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AceiteSimulacaoRequest("gestor.v3@empresa.com", "Aceito v3 com regra 2"))))
                .andExpect(status().isOk());

        long revisaoAtivarV3 = campanhaRepository.findById(campanhaId).get().getVersaoControle();
        mockMvc.perform(post("/api/v1/campanhas/" + campanhaId + "/ativar")
                        .header("X-Campanha-Revisao", revisaoAtivarV3)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtivarCampanhaRequest(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoVigente").value(3));

        // 2. Compare com a versão anterior (2 vs 3): a regra bloco-2 deve aparecer como ADICIONADA.
        MvcResult comp2e3Result = mockMvc.perform(get("/api/v1/campanhas/" + campanhaId + "/versoes/comparacao")
                        .param("versaoBase", "2")
                        .param("versaoComparada", "3"))
                .andExpect(status().isOk())
                .andReturn();

        System.out.println("=== PASSO 5.2: COMPARAÇÃO 2 vs 3 (ADICIONADA) ===");
        System.out.println(comp2e3Result.getResponse().getContentAsString());

        JsonNode comp2e3Json = objectMapper.readTree(comp2e3Result.getResponse().getContentAsString());
        JsonNode regras2e3 = comp2e3Json.get("alteracoesRegras");
        JsonNode regraBloco2 = null;
        for (JsonNode r : regras2e3) {
            if ("bloco-2".equals(r.get("identificadorLogico").asText())) {
                regraBloco2 = r;
                break;
            }
        }
        assertThat(regraBloco2).isNotNull();
        assertThat(regraBloco2.get("tipo").asText()).isEqualTo("ADICIONADA");

        // 3. Publique outra versão sem essa regra.
        CampanhaRequest editReqV4 = new CampanhaRequest(
                "Campanha Teste PR BACK-10 V4", "Campanha teste de versões",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                EstadoCampanha.DRAFT, List.of(regraV2) // Somente regraV2, removendo regraV3Extra
        );

        mockMvc.perform(put("/api/v1/campanhas/" + campanhaId)
                        .header("X-Campanha-Revisao", campanhaRepository.findById(campanhaId).get().getVersaoControle())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReqV4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoProposta").value(4));

        MvcResult simV4Result = mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SimulacaoCenariosRequest(
                                "2026-06", campanhaId, null, null, null, 50, 0, false, false))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID simV4Id = UUID.fromString(objectMapper.readTree(simV4Result.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/simulacoes/" + simV4Id + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AceiteSimulacaoRequest("gestor.v4@empresa.com", "Aceito v4 sem regra 2"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/campanhas/" + campanhaId + "/ativar")
                        .header("X-Campanha-Revisao", campanhaRepository.findById(campanhaId).get().getVersaoControle())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtivarCampanhaRequest(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoVigente").value(4));

        // 4. Compare novamente (3 vs 4): bloco-2 deve aparecer como REMOVIDA.
        MvcResult comp3e4Result = mockMvc.perform(get("/api/v1/campanhas/" + campanhaId + "/versoes/comparacao")
                        .param("versaoBase", "3")
                        .param("versaoComparada", "4"))
                .andExpect(status().isOk())
                .andReturn();

        System.out.println("=== PASSO 5.4: COMPARAÇÃO 3 vs 4 (REMOVIDA) ===");
        System.out.println(comp3e4Result.getResponse().getContentAsString());

        JsonNode comp3e4Json = objectMapper.readTree(comp3e4Result.getResponse().getContentAsString());
        JsonNode regras3e4 = comp3e4Json.get("alteracoesRegras");
        JsonNode regraBloco2Removida = null;
        for (JsonNode r : regras3e4) {
            if ("bloco-2".equals(r.get("identificadorLogico").asText())) {
                regraBloco2Removida = r;
                break;
            }
        }
        assertThat(regraBloco2Removida).isNotNull();
        assertThat(regraBloco2Removida.get("tipo").asText()).isEqualTo("REMOVIDA");
    }
}
