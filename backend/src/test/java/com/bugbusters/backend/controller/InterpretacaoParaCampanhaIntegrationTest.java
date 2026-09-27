package com.bugbusters.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que o JSON devolvido por /api/v1/interpretador/extrair-regra é
 * diretamente compatível com o payload aceito por /api/v1/campanhas —
 * simulando o passo de revisão humana entre as duas chamadas, exatamente como o
 * front faria: recebe a proposta, deixa o usuário revisar/editar, e só então
 * envia o pedido de salvar como rascunho.
 *
 * Se um dos dois DTOs (InterpretacaoRegraResponse / CampanhaRequest) divergir no
 * futuro, este teste quebra antes de virar um bug de integração em produção.
 */
@SpringBootTest
@ActiveProfiles("test")
class InterpretacaoParaCampanhaIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RestClient.Builder aiRestClientBuilder;

    @Autowired
    private com.bugbusters.backend.service.client.AiServiceClient aiServiceClient;

    private MockMvc mockMvc;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockServer = MockRestServiceServer.bindTo(aiRestClientBuilder).build();
        aiServiceClient.setAiRestClient(aiRestClientBuilder.build());
    }

    @Test
    @DisplayName("Fluxo completo: interpretar -> revisão humana -> salvar como rascunho, sem perda de campos")
    void deveIntegrarInterpretacaoAoCadastroDeCampanha() throws Exception {
        // 1. Simula a resposta do serviço Python, no formato real de InterpretacaoRegraResponse
        String respostaSimuladaPython = """
            {
              "canal": null,
              "codMarca": 10,
              "descrMarca": "PRETO",
              "codCargo": 100,
              "descriCargo": "VENDEDOR LOJA",
              "codLoja": 75,
              "taxa": 0.0350,
              "dataInicio": "2026-10-01",
              "dataFim": "2026-10-31",
              "confianca": 0.95,
              "pendencias": []
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(respostaSimuladaPython, MediaType.APPLICATION_JSON));

        // 2. Front chama a interpretação
        String resultadoInterpretacao = mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "texto": "Comissão de 3.5% para os vendedores da marca PRETO na loja 75 em outubro",
                                "contexto": {}
                            }
                            """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode proposta = objectMapper.readTree(resultadoInterpretacao);

        // 3. Revisão humana: usuário confirma os campos propostos e informa título
        //    (título e texto original não vêm da IA — são inseridos pelo usuário no form)
        String payloadCampanha = objectMapper.createObjectNode()
                .put("titulo", "Campanha Vendedores Marca Preto - Outubro")
                .put("textoOriginal", "Comissão de 3.5% para os vendedores da marca PRETO na loja 75 em outubro")
                .put("codMarca", proposta.get("codMarca").asInt())
                .put("descrMarca", proposta.get("descrMarca").asText())
                .put("codLoja", proposta.get("codLoja").asInt())
                .put("codCargo", proposta.get("codCargo").asInt())
                .put("descriCargo", proposta.get("descriCargo").asText())
                .put("taxa", proposta.get("taxa").decimalValue())
                .put("dataInicio", proposta.get("dataInicio").asText())
                .put("dataFim", proposta.get("dataFim").asText())
                .toString();

        // 4. Front envia o pedido de salvar como rascunho
        mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadCampanha))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.estado").value("DRAFT"))
                .andExpect(jsonPath("$.regra.status").value("DRAFT"))
                .andExpect(jsonPath("$.regra.codMarca").value(10))
                .andExpect(jsonPath("$.regra.descrMarca").value("PRETO"))
                .andExpect(jsonPath("$.regra.codLoja").value(75))
                .andExpect(jsonPath("$.regra.codCargo").value(100))
                .andExpect(jsonPath("$.regra.descriCargo").value("VENDEDOR LOJA"))
                .andExpect(jsonPath("$.regra.taxa").value(0.0350))
                .andExpect(jsonPath("$.regra.dataInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.regra.dataFim").value("2026-10-31"));
    }

    @Test
    @DisplayName("Interpretação com data final ausente preserva o null até o Spring aplicar os 30 dias padrão")
    void devePreservarAusenciaDeDataFimAteSalvarComoRascunho() throws Exception {
        String respostaSimuladaPython = """
            {
              "canal": "ECOMMERCE",
              "codMarca": null,
              "descrMarca": null,
              "codCargo": null,
              "descriCargo": null,
              "codLoja": null,
              "taxa": 0.0500,
              "dataInicio": "2026-10-01",
              "dataFim": null,
              "confianca": 0.80,
              "pendencias": ["Data final omitida; serão aplicados 30 dias de vigência padrão na confirmação."]
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(respostaSimuladaPython, MediaType.APPLICATION_JSON));

        String resultadoInterpretacao = mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "texto": "Comissão de 5% no ecommerce a partir de outubro",
                                "contexto": {}
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataFim").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode proposta = objectMapper.readTree(resultadoInterpretacao);

        String payloadCampanha = objectMapper.createObjectNode()
                .put("titulo", "Campanha Ecommerce Sem Data Fim")
                .put("textoOriginal", "Comissão de 5% no ecommerce a partir de outubro")
                .put("canal", proposta.get("canal").asText())
                .put("taxa", proposta.get("taxa").decimalValue())
                .put("dataInicio", proposta.get("dataInicio").asText())
                .toString();

        // O Spring aplica os 30 dias padrão só no momento de salvar como rascunho
        mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadCampanha))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regra.dataInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.regra.dataFim").value("2026-10-31"));
    }
}
