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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.endsWith;
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
 * front faria: recebe a proposta, deixa o usuário revisar/editar, e envia a coleção
 * de regras para salvar como rascunho.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "ai.service.url=http://localhost:8000")
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
    @DisplayName("Fluxo completo: interpretar -> revisão humana -> salvar coleção como rascunho, sem perda de campos")
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

        mockServer.expect(requestTo(endsWith("/api/v1/interpretar")))
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

        // 3. Revisão humana: usuário confirma os campos propostos, informa título e empacota na coleção de regras
        var root = objectMapper.createObjectNode();
        root.put("titulo", "Campanha Vendedores Marca Preto - Outubro");
        root.put("textoOriginal", "Comissão de 3.5% para os vendedores da marca PRETO na loja 75 em outubro");
        root.put("dataInicio", proposta.get("dataInicio").asText());
        root.put("dataFim", proposta.get("dataFim").asText());

        var regrasNode = root.putArray("regras");
        var item = regrasNode.addObject();
        item.put("codMarca", proposta.get("codMarca").asInt());
        item.put("descrMarca", proposta.get("descrMarca").asText());
        item.put("codLoja", proposta.get("codLoja").asInt());
        item.put("codCargo", proposta.get("codCargo").asInt());
        item.put("descriCargo", proposta.get("descriCargo").asText());
        item.put("taxa", proposta.get("taxa").decimalValue());

        String payloadCampanha = root.toString();

        // 4. Front envia o pedido de salvar como rascunho
        mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadCampanha))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.estado").value("DRAFT"))
                .andExpect(jsonPath("$.regras").isArray())
                .andExpect(jsonPath("$.regras[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.regras[0].codMarca").value(10))
                .andExpect(jsonPath("$.regras[0].descrMarca").value("PRETO"))
                .andExpect(jsonPath("$.regras[0].codLoja").value(75))
                .andExpect(jsonPath("$.regras[0].codCargo").value(100))
                .andExpect(jsonPath("$.regras[0].descriCargo").value("VENDEDOR LOJA"))
                .andExpect(jsonPath("$.regras[0].taxa").value(0.0350))
                .andExpect(jsonPath("$.regras[0].dataInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.regras[0].dataFim").value("2026-10-31"));
    }

    @Test
    @DisplayName("Interpretação com data final ausente preserva o null até o Spring aplicar os 30 dias padrão na campanha e regras")
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

        mockServer.expect(requestTo(endsWith("/api/v1/interpretar")))
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

        var root = objectMapper.createObjectNode();
        root.put("titulo", "Campanha Ecommerce Sem Data Fim");
        root.put("textoOriginal", "Comissão de 5% no ecommerce a partir de outubro");
        root.put("dataInicio", proposta.get("dataInicio").asText());

        var regrasNode = root.putArray("regras");
        var item = regrasNode.addObject();
        item.put("canal", proposta.get("canal").asText());
        item.put("taxa", proposta.get("taxa").decimalValue());

        String payloadCampanha = root.toString();

        // O Spring aplica os 30 dias padrão no momento de salvar a campanha e propaga para todas as regras
        mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadCampanha))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.dataFim").value("2026-10-31"))
                .andExpect(jsonPath("$.regras[0].dataInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.regras[0].dataFim").value("2026-10-31"));
    }
}
