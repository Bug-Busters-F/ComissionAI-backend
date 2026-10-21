package com.bugbusters.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampanhaMultiplasRegrasTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private long criar(String json) throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/campanhas").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Object id = new JsonMapper().readValue(resposta, java.util.Map.class).get("id");
        return ((Number) id).longValue();
    }

    private static final String DUAS_REGRAS = """
            {"titulo":"Campanha N regras","textoOriginal":"3% até 10 mil e 4% acima, mais bônus",
             "dataInicio":"2026-10-01","dataFim":"2026-10-31",
             "regras":[
               {"codLoja":75,"taxa":0.0300,"valorMaximo":10000.00,"maxInclusivo":false,"bonusValorFixo":25.00},
               {"codLoja":75,"taxa":0.0400,"valorMinimo":10000.00,"bonusPercentual":0.0100,"canal":"ecommerce"}
             ]}
            """;

    @Test
    @DisplayName("POST com lista de regras cria N regras com faixa e bônus, herdando a vigência da campanha")
    void deveCriarCampanhaComVariasRegras() throws Exception {
        mockMvc.perform(post("/api/v1/campanhas").contentType(MediaType.APPLICATION_JSON).content(DUAS_REGRAS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regras", hasSize(2)))
                .andExpect(jsonPath("$.regras[0].taxa", is(0.03)))
                .andExpect(jsonPath("$.regras[0].valorMaximo", is(10000.0)))
                .andExpect(jsonPath("$.regras[0].maxInclusivo", is(false)))
                .andExpect(jsonPath("$.regras[0].bonusValorFixo", is(25.0)))
                .andExpect(jsonPath("$.regras[0].dataInicio", is("2026-10-01")))
                .andExpect(jsonPath("$.regras[1].dataFim", is("2026-10-31")))
                .andExpect(jsonPath("$.regras[1].canal", is("ECOMMERCE")))
                .andExpect(jsonPath("$.regras[1].bonusPercentual", is(0.01)))
                .andExpect(jsonPath("$.regras[1].status", is("DRAFT")));
    }

    @Test
    @DisplayName("GET, alteração de estado e PUT tratam todas as regras da campanha")
    void deveGerenciarCicloDeVidaComVariasRegras() throws Exception {
        long id = criar(DUAS_REGRAS);

        mockMvc.perform(get("/api/v1/campanhas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regras", hasSize(2)));

        // Simular e registrar aceite para conformidade BACK-8
        String simResponse = mockMvc.perform(post("/api/v1/simulacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"competencia\":\"2026-10\",\"campanhaId\":%d}", id)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String simId = new JsonMapper().readValue(simResponse, java.util.Map.class).get("id").toString();

        mockMvc.perform(post("/api/v1/simulacoes/" + simId + "/aceite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aceitoPor\":\"gestor@empresa.com\",\"motivo\":\"Aceite inicial\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/campanhas/" + id + "/estado").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ATIVA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regras[0].status", is("ATIVA")))
                .andExpect(jsonPath("$.regras[1].status", is("ATIVA")));

        String nova = """
                {"titulo":"Campanha N regras","textoOriginal":"revisada","dataInicio":"2026-10-01","dataFim":"2026-10-31",
                 "regras":[{"codLoja":75,"taxa":0.0350}]}
                """;
        // Edição substitui regras e, para segurança, invalida aceite e retorna para DRAFT
        mockMvc.perform(put("/api/v1/campanhas/" + id).contentType(MediaType.APPLICATION_JSON).content(nova))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regras", hasSize(1)))
                .andExpect(jsonPath("$.regras[0].taxa", is(0.035)))
                .andExpect(jsonPath("$.regras[0].status", is("DRAFT")));

        // Nova simulação e aceite da edição revisada para publicação em produção
        String simNovaResp = mockMvc.perform(post("/api/v1/simulacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"competencia\":\"2026-10\",\"campanhaId\":%d}", id)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String simNovaId = new JsonMapper().readValue(simNovaResp, java.util.Map.class).get("id").toString();

        mockMvc.perform(post("/api/v1/simulacoes/" + simNovaId + "/aceite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aceitoPor\":\"gestor@empresa.com\",\"motivo\":\"Aceite da revisão\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/campanhas/" + id + "/ativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regras[0].status", is("ATIVA")));

        mockMvc.perform(get("/api/v1/campanhas/" + id))
                .andExpect(jsonPath("$.regras", hasSize(1)));
    }

    @Test
    @DisplayName("Validações: faixa incoerente e bônus inválido retornam 400")
    void deveValidarEntrada() throws Exception {
        String faixaInvertida = """
                {"titulo":"X","textoOriginal":"x","regras":[{"taxa":0.03,"valorMinimo":500.00,"valorMaximo":100.00}]}
                """;
        mockMvc.perform(post("/api/v1/campanhas").contentType(MediaType.APPLICATION_JSON).content(faixaInvertida))
                .andExpect(status().isBadRequest());

        String bonusInvalido = """
                {"titulo":"X","textoOriginal":"x","regras":[{"taxa":0.03,"bonusPercentual":1.5}]}
                """;
        mockMvc.perform(post("/api/v1/campanhas").contentType(MediaType.APPLICATION_JSON).content(bonusInvalido))
                .andExpect(status().isBadRequest());

        String bonusFixoNegativo = """
                {"titulo":"X","textoOriginal":"x","regras":[{"taxa":0.03,"bonusValorFixo":-10.00}]}
                """;
        mockMvc.perform(post("/api/v1/campanhas").contentType(MediaType.APPLICATION_JSON).content(bonusFixoNegativo))
                .andExpect(status().isBadRequest());
    }
}
