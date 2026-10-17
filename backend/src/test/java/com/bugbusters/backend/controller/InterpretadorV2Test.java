package com.bugbusters.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

import com.bugbusters.backend.service.client.AiServiceClient;

@SpringBootTest
@ActiveProfiles("test")
class InterpretadorV2Test {

    @Autowired private WebApplicationContext context;
    @Autowired private RestClient.Builder aiRestClientBuilder;
    @Autowired private AiServiceClient aiServiceClient;

    private MockMvc mockMvc;
    private MockRestServiceServer mockServer;
    private RestClient restClientOriginal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        restClientOriginal = aiServiceClient.getAiRestClient();
        mockServer = MockRestServiceServer.bindTo(aiRestClientBuilder).build();
        aiServiceClient.setAiRestClient(aiRestClientBuilder.build());
    }

    @AfterEach
    void tearDown() {
        aiServiceClient.setV2Enabled(false);
        aiServiceClient.setAiRestClient(restClientOriginal);
    }

    private static final String CORPO = "{\"texto\":\"3% para vendedores da loja 75 e 4% para gerentes\"}";

    @Test
    @DisplayName("v2 desabilitado (padrão): extrair-propostas responde 404 e não chama o serviço de IA")
    void deveManterV2DesabilitadoPorPadrao() throws Exception {
        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isNotFound());
        mockServer.verify();
    }

    @Test
    @DisplayName("v2 habilitado: chama /api/v2/interpretar e repassa a lista de propostas")
    void deveRepassarPropostasV2() throws Exception {
        aiServiceClient.setV2Enabled(true);
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess("{\"status\":\"PROPOSTA_COM_PENDENCIAS\",\"propostas\":[{\"idLocal\":\"p1\"},{\"idLocal\":\"p2\"}]}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PROPOSTA_COM_PENDENCIAS")))
                .andExpect(jsonPath("$.propostas[1].idLocal", is("p2")));
        mockServer.verify();
    }

    @Test
    @DisplayName("v2 habilitado: vigência e competência vão no topo do payload e o contexto só leva os ids da campanha")
    void deveMoverVigenciaECompetenciaParaOTopoDoPayload() throws Exception {
        aiServiceClient.setV2Enabled(true);
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.vigenciaCampanha.inicio").value("2026-09-01"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.vigenciaCampanha.fim").value("2026-09-30"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.competencia").value("2026-09"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.contexto.campanhaId").value("12"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.contexto.vigenciaCampanha").doesNotExist())
                .andExpect(MockRestRequestMatchers.jsonPath("$.contexto.dicionario_dimensoes").doesNotExist())
                .andRespond(withSuccess("{\"status\":\"SEM_PROPOSTA\",\"propostas\":[]}", MediaType.APPLICATION_JSON));

        String corpo = "{\"texto\":\"2% para gerentes\",\"contexto\":{\"campanhaId\":\"12\","
                + "\"vigenciaCampanha\":{\"inicio\":\"2026-09-01\",\"fim\":\"2026-09-30\"},\"competencia\":\"2026-09\"}}";
        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
        mockServer.verify();
    }
}
