package com.bugbusters.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
class InterpretadorAgenteTest {

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
        aiServiceClient.setAiRestClient(restClientOriginal);
    }

    private static final String RESPOSTA_AGENTE = """
            {"status":"PROPOSTA_COM_PENDENCIAS",
             "propostas":[
               {"idLocal":"p1","condicoes":{"matricula":"MATRIC-9"},"taxa":"0.0500",
                "bonus":{"percentual":null,"valorFixo":"20.00"},
                "dataInicio":"2026-09-01","dataFim":"2026-09-30",
                "origem":{"trecho":"A matrícula MATRIC-9 ganha 5% e mais R$ 20 por venda"},
                "referencias":[{"fonte":"CADASTRO","chave":{"matricula":"MATRIC-9"}}],
                "pendencias":[{"codigo":"VIGENCIA_INCOMPATIVEL","campo":"dataInicio","mensagem":"O texto cita outubro; mantida a vigência da campanha."}]},
               {"idLocal":"p2","condicoes":{"codLoja":75},"taxa":"0.0300",
                "dataInicio":"2026-09-01","dataFim":"2026-09-30",
                "origem":{"trecho":"3% para vendedores da loja 75"},
                "pendencias":[{"codigo":"AMBIGUIDADE_CARGO","campo":"condicoes.codCargo","mensagem":"'vendedores' corresponde a VENDEDOR LOJA ou VENDEDOR BALCAO."}]}
             ],
             "pendenciasGerais":[{"codigo":"TOOL_FALHA","mensagem":"O modelo ficou indisponível durante a análise."}],
             "trechosNaoMapeados":[{"trecho":"lembrem do uniforme novo"}]}
            """;

    @Test
    @DisplayName("Envia vigência e competência no topo do payload do agente; o contexto leva só os ids da campanha")
    void deveMontarPayloadDoAgente() throws Exception {
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.vigenciaCampanha.inicio").value("2026-09-01"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.vigenciaCampanha.fim").value("2026-09-30"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.competencia").value("2026-09"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.contexto.campanhaId").value("12"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.contexto.vigenciaCampanha").doesNotExist())
                .andRespond(withSuccess("{\"status\":\"SEM_PROPOSTA\",\"propostas\":[]}", MediaType.APPLICATION_JSON));

        String corpo = "{\"texto\":\"2% para gerentes\",\"contexto\":{\"campanhaId\":\"12\","
                + "\"vigenciaCampanha\":{\"inicio\":\"2026-09-01\",\"fim\":\"2026-09-30\"},\"competencia\":\"2026-09\"}}";
        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas", hasSize(0)));
        mockServer.verify();
    }

    @Test
    @DisplayName("Converte a resposta do agente: bônus, referências do cadastro, gravidade das pendências e pendências gerais")
    void deveConverterRespostaDoAgente() throws Exception {
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(RESPOSTA_AGENTE, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"pedido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas", hasSize(2)))
                .andExpect(jsonPath("$.propostas[0].filtros.matricula", is("MATRIC-9")))
                .andExpect(jsonPath("$.propostas[0].bonusValorFixo", is(20.0)))
                .andExpect(jsonPath("$.propostas[0].referenciasConsultadas[0].tipo", is("MATRICULA")))
                .andExpect(jsonPath("$.propostas[0].origensPorCampo.matricula", is("BASE_DADOS")))
                .andExpect(jsonPath("$.propostas[0].pendencias[0].severidade", is("AVISO")))
                .andExpect(jsonPath("$.propostas[0].completa", is(true)))
                .andExpect(jsonPath("$.propostas[1].pendencias[0].codigo", is("AMBIGUIDADE_CARGO")))
                .andExpect(jsonPath("$.propostas[1].pendencias[0].severidade", is("IMPEDIMENTO")))
                .andExpect(jsonPath("$.propostas[1].completa", is(false)))
                .andExpect(jsonPath("$.todasCompletas", is(false)))
                .andExpect(jsonPath("$.pendenciasGerais", hasSize(2)))
                .andExpect(jsonPath("$.pendenciasGerais[0].codigo", is("TOOL_FALHA")))
                .andExpect(jsonPath("$.pendenciasGerais[1].codigo", is("TRECHO_NAO_MAPEADO")));
        mockServer.verify();
    }
}
