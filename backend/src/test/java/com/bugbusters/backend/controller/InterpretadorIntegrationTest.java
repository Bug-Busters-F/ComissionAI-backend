package com.bugbusters.backend.controller;

import com.bugbusters.backend.service.client.AiServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InterpretadorIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private RestClient.Builder aiRestClientBuilder;

    @Autowired
    private AiServiceClient aiServiceClient;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockServer = MockRestServiceServer.bindTo(aiRestClientBuilder).build();
        aiServiceClient.setAiRestClient(aiRestClientBuilder.build());
    }

    @Test
    @DisplayName("Deve chamar Python, sanitizar retorno e apontar pendências sem persistir")
    void deveProcessarInterpretacaoComSucesso() throws Exception {
        String respostaSimuladaPython = """
            {
              "canal": "ecommerce",
              "taxa": 0.0500,
              "dataInicio": "2026-12-01",
              "dataFim": "2026-12-31",
              "confianca": 0.98,
              "pendencias": []
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(respostaSimuladaPython, MediaType.APPLICATION_JSON));

        String requestBody = """
            {
              "texto": "pagar 5% no ecommerce em dezembro",
              "contexto": {}
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("ECOMMERCE"))
                .andExpect(jsonPath("$.taxa").value(0.05))
                .andExpect(jsonPath("$.dataInicio").value("2026-12-01"))
                .andExpect(jsonPath("$.dataFim").value("2026-12-31"));

        mockServer.verify();
    }

    @Test
    @DisplayName("Deve retornar 503 quando o serviço de IA em Python retornar erro interno")
    void deveRetornar503QuandoAiRetornarErro() throws Exception {
        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        String requestBody = """
            {
              "texto": "regra de teste com falha",
              "contexto": {}
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Serviço de IA Indisponível"));

        mockServer.verify();
    }

    @Test
    @DisplayName("Deve sanitizar e apontar pendências quando resposta do modelo for inconsistente")
    void deveApontarPendenciasQuandoRetornoInconsistente() throws Exception {
        String respostaSimuladaComInconsistencias = """
            {
              "canal": null,
              "taxa": 1.5000,
              "dataInicio": "2026-12-31",
              "dataFim": "2026-12-01",
              "confianca": 0.40,
              "pendencias": ["Termo de canal ambíguo."]
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(respostaSimuladaComInconsistencias, MediaType.APPLICATION_JSON));

        String requestBody = """
            {
              "texto": "150% em qualquer lugar",
              "contexto": {}
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").doesNotExist())
                .andExpect(jsonPath("$.taxa").doesNotExist())
                .andExpect(jsonPath("$.pendencias", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(2))));

        mockServer.verify();
    }

    @Test
    @DisplayName("Deve rejeitar requisição sem texto com 400 Bad Request")
    void deveRejeitarTextoVazio() throws Exception {
        String requestBody = """
            {
              "texto": "   "
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validacoes[0].campo").value("texto"));
    }

    @Test
    @DisplayName("Deve extrair e normalizar dimensões reais (marca, cargo, loja) sem descartar parâmetros válidos")
    void deveInterpretarDimensoesReaisComSucesso() throws Exception {
        String respostaSimuladaPython = """
            {
              "canal": "loja_fisica",
              "codMarca": 10,
              "descrMarca": "preto",
              "codCargo": 100,
              "descriCargo": "vendedor loja",
              "codLoja": 75,
              "taxa": 0.0250,
              "dataInicio": "2025-12-01",
              "dataFim": "2025-12-31",
              "confianca": 0.95,
              "pendencias": []
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(respostaSimuladaPython, MediaType.APPLICATION_JSON));

        String requestBody = """
            {
              "texto": "Comissão de 2.5% para vendedor loja da marca preto na loja 75 em dezembro",
              "contexto": {}
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("LOJA_FISICA"))
                .andExpect(jsonPath("$.codMarca").value(10))
                .andExpect(jsonPath("$.descrMarca").value("PRETO"))
                .andExpect(jsonPath("$.codCargo").value(100))
                .andExpect(jsonPath("$.descriCargo").value("VENDEDOR LOJA"))
                .andExpect(jsonPath("$.codLoja").value(75))
                .andExpect(jsonPath("$.taxa").value(0.025))
                .andExpect(jsonPath("$.dataInicio").value("2025-12-01"))
                .andExpect(jsonPath("$.dataFim").value("2025-12-31"));

        mockServer.verify();
    }

    @Test
    @DisplayName("Cenário C: Deve apontar pendência de ambiguidade para cargo 150 e não preencher descriCargo automaticamente")
    void deveTratarAmbiguidadeCargo150SemAutoPreenchimento() throws Exception {
        String respostaSimuladaPython = """
            {
              "canal": null,
              "codMarca": 10,
              "descrMarca": "PRETO",
              "codCargo": 150,
              "descriCargo": null,
              "codLoja": null,
              "taxa": 0.0100,
              "dataInicio": "2026-09-01",
              "dataFim": "2026-09-30",
              "confianca": 0.70,
              "pendencias": [
                "Cargo 150 possui múltiplas funções (GERENTE DE LOJA, GERENTE QUIOSQUE). Favor especificar o cargo exato."
              ]
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.contexto.ano_referencia").exists())
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.contexto.dicionario_dimensoes.cargos").exists())
                .andRespond(withSuccess(respostaSimuladaPython, MediaType.APPLICATION_JSON));

        String requestBody = """
            {
              "texto": "Quero comissão de 1% para gerentes da marca preto",
              "contexto": {}
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codMarca").value(10))
                .andExpect(jsonPath("$.descrMarca").value("PRETO"))
                .andExpect(jsonPath("$.codCargo").value(150))
                .andExpect(jsonPath("$.descriCargo").doesNotExist())
                .andExpect(jsonPath("$.pendencias", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("Cargo 150 possui múltiplas funções")
                )));

        mockServer.verify();
    }
}