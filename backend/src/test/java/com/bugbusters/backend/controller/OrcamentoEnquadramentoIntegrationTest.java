package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.ConfirmarOrcamentoCampanhaRequest;
import com.bugbusters.backend.dto.campanha.EncaminharCampanhaGerenteRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.dto.simulacao.AceiteSimulacaoRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrcamentoEnquadramentoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        Store store = storeRepository.findByCode(100).orElseGet(() -> {
            Store s = new Store();
            s.setCode(100);
            s.setDescription("Loja Orçamento Teste");
            return storeRepository.save(s);
        });

        Brand brand = brandRepository.findByCode(1).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(1);
            b.setDescription("SAMSUNG");
            return brandRepository.save(b);
        });

        Position pos = positionRepository.findByCode(10).orElseGet(() -> {
            Position p = new Position();
            p.setCode(10);
            p.setDescription("VENDEDOR");
            return positionRepository.save(p);
        });

        Registration reg = registrationRepository.findByRegistration("MAT-99901").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MAT-99901");
            r.setStore(store);
            r.setPosition(pos);
            r.setAdmissDate(LocalDate.of(2025, 1, 1));
            return registrationRepository.save(r);
        });

        if (baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(1, 10).isEmpty()) {
            BaseComiss base = new BaseComiss(brand, pos, new BigDecimal("0.0300"), LocalDate.of(2026, 10, 1));
            baseComissRepository.save(base);
        }

        // Venda 1: R$ 10.000,00 -> comissão a 5% = R$ 500,00
        Sale sale1 = new Sale();
        sale1.setId(UUID.randomUUID());
        sale1.setSaleDate(LocalDate.of(2026, 10, 15));
        sale1.setValue(new BigDecimal("10000.00"));
        sale1.setSaleChannel("ECOMMERCE");
        sale1.setBrand(brand);
        sale1.setStore(store);
        sale1.setRegistration(reg);
        saleRepository.save(sale1);

        // Venda 2: R$ 20.000,00 -> comissão a 5% = R$ 1.000,00
        Sale sale2 = new Sale();
        sale2.setId(UUID.randomUUID());
        sale2.setSaleDate(LocalDate.of(2026, 10, 20));
        sale2.setValue(new BigDecimal("20000.00"));
        sale2.setSaleChannel("ECOMMERCE");
        sale2.setBrand(brand);
        sale2.setStore(store);
        sale2.setRegistration(reg);
        saleRepository.save(sale2);
    }

    @Test
    @DisplayName("BACK-8: Fluxo completo de orçamento, confirmação, simulação nos 4 cenários e bloqueio de encaminhamento ao gerente")
    void deveExecutarFluxoCompletoOrcamentoEEnquadramento() throws Exception {
        // 1. Criar regra e campanha com orçamento não confirmado (extraído pela IA)
        RegraItemRequest regra = new RegraItemRequest(
                "bloco-orc-1", "Comissão 5% E-commerce", "Regra Orçamento E-commerce", "ECOMMERCE",
                1, null, null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0500"),
                "5% fixo sobre e-commerce", "taxa = 0.05",
                List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest criarReq = new CampanhaRequest(
                "Campanha Teste Orçamento BACK-8", "Proposta com orçamento de R$ 1.000,00",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30),
                EstadoCampanha.DRAFT, List.of(regra),
                null,
                new BigDecimal("1000.00"), // Teto estipulado de R$ 1.000,00
                new BigDecimal("1000.00"),
                false, // NÃO confirmado ainda
                null,
                null
        );

        MvcResult criarRes = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criarReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.orcamento").value(1000.00))
                .andExpect(jsonPath("$.orcamentoConfirmado").value(false))
                .andExpect(jsonPath("$.origemOrcamento").value("EXTRAIDO_IA"))
                .andReturn();

        long campanhaId = objectMapper.readTree(criarRes.getResponse().getContentAsString()).get("id").asLong();

        // 2. Tentar simular sem confirmar o orçamento -> Deve ser rejeitado (HTTP 400)
        SimulacaoCenariosRequest simReq = new SimulacaoCenariosRequest(
                "2026-10", campanhaId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30),
                null, 50, 0, false, false
        );

        mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("deve possuir orçamento monetário estritamente positivo e formalmente confirmado")));

        // 3. Confirmar o orçamento explicitamente pelo endpoint PATCH /api/v1/campanhas/{id}/orcamento
        // Definir teto de R$ 1.200,00 (suficiente para 80% que é R$ 1.200, mas menor que 100% que é R$ 1.500)
        ConfirmarOrcamentoCampanhaRequest confirmarReq = new ConfirmarOrcamentoCampanhaRequest(
                new BigDecimal("1200.00"),
                new BigDecimal("1000.00"),
                "analista.financeiro@empresa.com",
                "Orçamento validado com a gerência comercial",
                null
        );

        mockMvc.perform(patch("/api/v1/campanhas/" + campanhaId + "/orcamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmarReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orcamento").value(1200.00))
                .andExpect(jsonPath("$.orcamentoConfirmado").value(true))
                .andExpect(jsonPath("$.orcamentoConfirmadoPor").value("analista.financeiro@empresa.com"))
                .andExpect(jsonPath("$.origemOrcamento").value("ALTERADO_USUARIO"));

        // 4. Executar a simulação agora com orçamento confirmado -> Deve ter sucesso e calcular enquadramento
        MvcResult simRes = mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enquadramentoOrcamento.orcamentoCampanha").value(1200.00))
                .andExpect(jsonPath("$.enquadramentoOrcamento").exists())
                .andReturn();

        JsonNode simJson = objectMapper.readTree(simRes.getResponse().getContentAsString());
        UUID simId = UUID.fromString(simJson.get("id").asText());

        // Validar que os 4 cenários usam o mesmo escopo de vendas
        int escopoTotalVendas = simJson.get("enquadramentoOrcamento").get("totalVendasEscopoComum").asInt();
        assertThat(escopoTotalVendas).isEqualTo(2);

        JsonNode c80 = simJson.get("cenario80").get("enquadramento");
        JsonNode c100 = simJson.get("cenario100").get("enquadramento");
        JsonNode c120 = simJson.get("cenario120").get("enquadramento");
        JsonNode c150 = simJson.get("cenario150").get("enquadramento");

        assertThat(c80.get("totalVendasEscopo").asInt()).isEqualTo(2);
        assertThat(c100.get("totalVendasEscopo").asInt()).isEqualTo(2);
        assertThat(c120.get("totalVendasEscopo").asInt()).isEqualTo(2);
        assertThat(c150.get("totalVendasEscopo").asInt()).isEqualTo(2);

        // Cenário 80%: comissão = R$ 1.200,00 -> Enquadrado (orcamento = 1.200,00)
        assertThat(c80.get("dentroDoOrcamento").asBoolean()).isTrue();
        // Cenário 100%: comissão = R$ 1.500,00 -> NÃO enquadrado (excede em R$ 300,00)
        assertThat(c100.get("dentroDoOrcamento").asBoolean()).isFalse();
        assertThat(c100.get("statusEnquadramento").asText()).isEqualTo("ACIMA_DO_TETO");
        assertThat(c100.get("excedenteOrcamento").asDouble()).isEqualTo(300.00);

        // 5. Tentar encaminhar campanha ao gerente enquanto o cenário 100% excede o orçamento -> Bloqueio HTTP 400
        EncaminharCampanhaGerenteRequest encaminharReq = new EncaminharCampanhaGerenteRequest(
                "analista.financeiro@empresa.com",
                "Encaminhamento para validação do gerente"
        );

        mockMvc.perform(post("/api/v1/campanhas/" + campanhaId + "/encaminhar-gerente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encaminharReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Não é permitido encaminhar ao gerente: o cenário base de 100% da meta excede o orçamento estabelecido")));

        // 5.1 Tentar aceitar simulação excedida -> Bloqueio HTTP 400
        AceiteSimulacaoRequest aceiteReq = new AceiteSimulacaoRequest("gestor@empresa.com", "Tentativa de aceite excedido");
        mockMvc.perform(post("/api/v1/simulacoes/" + simId + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceiteReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("excede o teto orçamentário da campanha")));

        // 6. Atualizar o orçamento para valor suficiente (R$ 2.000,00)
        ConfirmarOrcamentoCampanhaRequest aumentoOrcamentoReq = new ConfirmarOrcamentoCampanhaRequest(
                new BigDecimal("2000.00"),
                new BigDecimal("1000.00"),
                "diretoria@empresa.com",
                "Suplementação orçamentária aprovada pela diretoria",
                null
        );

        mockMvc.perform(patch("/api/v1/campanhas/" + campanhaId + "/orcamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aumentoOrcamentoReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orcamento").value(2000.00));

        // 7. Simular novamente com o orçamento aprovado
        MvcResult novaSimRes = mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enquadramentoOrcamento.orcamentoCampanha").value(2000.00))
                .andExpect(jsonPath("$.cenario100.enquadramento.dentroDoOrcamento").value(true))
                .andExpect(jsonPath("$.cenario100.enquadramento.statusEnquadramento").value("ENQUADRADO"))
                .andExpect(jsonPath("$.cenario100.enquadramento.saldoOrcamento").value(500.00)) // 2000 - 1500 = 500
                .andReturn();

        UUID novaSimId = UUID.fromString(objectMapper.readTree(novaSimRes.getResponse().getContentAsString()).get("id").asText());

        // 8. Agora o aceite da simulação deve ser permitido com sucesso
        mockMvc.perform(post("/api/v1/simulacoes/" + novaSimId + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AceiteSimulacaoRequest("gestor@empresa.com", "Simulação aprovada com folga orçamentária"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAceite").value("ACEITA"));

        // 9. Agora o encaminhamento ao gerente deve ter sucesso
        mockMvc.perform(post("/api/v1/campanhas/" + campanhaId + "/encaminhar-gerente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encaminharReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encaminhadaGerente").value(true))
                .andExpect(jsonPath("$.encaminhadaGerentePor").value("analista.financeiro@empresa.com"));
    }
    @Test
    @DisplayName("PUT /api/v1/campanhas/{id} deve calcular e atualizar origemOrcamento corretamente")
    void deveAtualizarOrigemOrcamentoViaPutCampanha() throws Exception {
        RegraItemRequest regra = new RegraItemRequest(
                "bloco-orc-teste", "Comissão 5% E-commerce", "Regra Orçamento E-commerce", "ECOMMERCE",
                1, null, null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0500"),
                "5% fixo sobre e-commerce", "taxa = 0.05",
                List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest criarReq = new CampanhaRequest(
                "Campanha Teste Origem Orcamento",
                "Texto descritivo",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                List.of(regra),
                null,
                null,
                null,
                null,
                null
        );

        MvcResult criarRes = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criarReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(criarRes.getResponse().getContentAsString()).get("id").asLong();

        // 1. PUT com orçamento alterado em relação à IA
        CampanhaRequest putAlteradoReq = new CampanhaRequest(
                "Campanha Teste Origem Orcamento Atualizada",
                "Texto descritivo",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                List.of(regra),
                new BigDecimal("2500.00"),
                new BigDecimal("2000.00"),
                true,
                "usuario.teste@empresa.com",
                "Justificativa de ajuste"
        );

        mockMvc.perform(put("/api/v1/campanhas/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putAlteradoReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orcamento").value(2500.00))
                .andExpect(jsonPath("$.orcamentoConfirmado").value(true))
                .andExpect(jsonPath("$.origemOrcamento").value("ALTERADO_USUARIO"));

        // 2. PUT confirmando o mesmo valor da IA
        CampanhaRequest putConfirmadoReq = new CampanhaRequest(
                "Campanha Teste Origem Orcamento Confirmada",
                "Texto descritivo",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                List.of(regra),
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                true,
                "usuario.teste@empresa.com",
                "Valor da IA confirmado"
        );

        mockMvc.perform(put("/api/v1/campanhas/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putConfirmadoReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orcamento").value(2000.00))
                .andExpect(jsonPath("$.orcamentoConfirmado").value(true))
                .andExpect(jsonPath("$.origemOrcamento").value("CONFIRMADO_USUARIO"));
    }
}
