package com.bugbusters.backend.controller;

import com.bugbusters.backend.service.client.AiServiceClient;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InterpretadorPropostasIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RestClient.Builder aiRestClientBuilder;

    @Autowired
    private AiServiceClient aiServiceClient;

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
    @DisplayName("Deve extrair múltiplas propostas de regras com faixas e ajustes sobre a base")
    void deveExtrairMultiplasPropostasComSucesso() throws Exception {
        String respostaSimuladaPython1 = """
            {
              "canal": "ECOMMERCE",
              "codMarca": 10,
              "descrMarca": "PRETO",
              "codCargo": null,
              "descriCargo": null,
              "codLoja": null,
              "taxa": 0.0500,
              "dataInicio": "2026-10-01",
              "dataFim": "2026-10-31",
              "confianca": 0.95,
              "pendencias": []
            }
            """;

        String respostaSimuladaPython2 = """
            {
              "canal": null,
              "codMarca": null,
              "descrMarca": null,
              "codCargo": null,
              "descriCargo": null,
              "codLoja": 75,
              "taxa": 0.0450,
              "dataInicio": "2026-10-01",
              "dataFim": "2026-10-31",
              "confianca": 0.92,
              "pendencias": []
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(respostaSimuladaPython1, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(respostaSimuladaPython2, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "texto": "1. Pagar 5% no canal ecommerce durante outubro.\\n2. Para a loja 75, pagar taxa base + 1.5% em vendas acima de R$ 5.000.",
                                "contexto": {
                                    "ano_referencia": 2026
                                }
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeBlocos", is(2)))
                .andExpect(jsonPath("$.propostas", hasSize(2)))
                .andExpect(jsonPath("$.propostas[0].blocoId", is("bloco-1")))
                .andExpect(jsonPath("$.propostas[0].filtros.canal", is("ECOMMERCE")))
                .andExpect(jsonPath("$.propostas[0].taxaFinal", is(0.0500)))
                .andExpect(jsonPath("$.propostas[1].blocoId", is("bloco-2")))
                .andExpect(jsonPath("$.propostas[1].filtros.codLoja", is(75)))
                .andExpect(jsonPath("$.propostas[1].condicaoValor.valorMinimo", is(5000)))
                .andExpect(jsonPath("$.propostas[1].operacaoBase.tipoOperacao", is("ACRESCIMO_PONTOS")))
                .andExpect(jsonPath("$.propostas[1].explicacao", notNullValue()))
                .andExpect(jsonPath("$.propostas[1].pythonEquivalente", containsString("def calcular_comissao")));
    }

    @Test
    @DisplayName("Deve reinterpretar bloco específico preservando a lista de propostas")
    void deveReinterpretarBlocoEspecifico() throws Exception {
        String respostaSimuladaPython = """
            {
              "canal": null,
              "codMarca": null,
              "descrMarca": null,
              "codCargo": null,
              "descriCargo": null,
              "codLoja": 75,
              "taxa": 0.0500,
              "dataInicio": "2026-10-01",
              "dataFim": "2026-10-31",
              "confianca": 0.95,
              "pendencias": []
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(respostaSimuladaPython, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/reinterpretar-bloco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "blocoId": "bloco-1",
                                "instrucaoAjuste": "Mudar a condição para vendas acima de R$ 10.000",
                                "propostasAtuais": [
                                    {
                                        "blocoId": "bloco-1",
                                        "trechoOrigem": "Loja 75 pagar 5%",
                                        "filtros": { "codLoja": 75 },
                                        "condicaoValor": { "valorMinimo": 5000.00, "minInclusivo": false },
                                        "operacaoBase": { "tipoOperacao": "DEFINIR_TAXA", "valorAjuste": 0.0500 },
                                        "taxaFinal": 0.0500,
                                        "vigencia": { "dataInicio": "2026-10-01", "dataFim": "2026-10-31" }
                                    }
                                ],
                                "contexto": {}
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocoModificadoId", is("bloco-1")))
                .andExpect(jsonPath("$.propostasAtualizadas", hasSize(1)))
                .andExpect(jsonPath("$.propostasAtualizadas[0].condicaoValor.valorMinimo", is(10000)));
    }

    @Test
    @DisplayName("Deve atualizar artefatos explicativos após edição manual de campos no frontend")
    void deveAtualizarArtefatosAposEdicaoManual() throws Exception {
        mockMvc.perform(post("/api/v1/interpretador/atualizar-artefatos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "proposta": {
                                    "blocoId": "bloco-1",
                                    "trechoOrigem": "Vendas no e-commerce",
                                    "filtros": {
                                        "canal": "ECOMMERCE",
                                        "codMarca": 10,
                                        "descrMarca": "PRETO"
                                    },
                                    "condicaoValor": {
                                        "valorMinimo": 2000.00,
                                        "minInclusivo": false
                                    },
                                    "operacaoBase": {
                                        "tipoOperacao": "DEFINIR_TAXA",
                                        "valorAjuste": 0.0600
                                    },
                                    "taxaFinal": 0.0600,
                                    "vigencia": {
                                        "dataInicio": "2026-10-01",
                                        "dataFim": "2026-10-31"
                                    }
                                }
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocoId", is("bloco-1")))
                .andExpect(jsonPath("$.taxaCalculada", is(0.0600)))
                .andExpect(jsonPath("$.explicacao", containsString("6.00%")))
                .andExpect(jsonPath("$.explicacao", containsString("ECOMMERCE")))
                .andExpect(jsonPath("$.pythonEquivalente", containsString("0.0600")))
                .andExpect(jsonPath("$.completa", is(true)));
    }

    @Test
    @DisplayName("Deve consultar catálogo de informações de domínio conhecidas do cliente")
    void deveConsultarCatalogoDominioCompleto() throws Exception {
        mockMvc.perform(get("/api/v1/interpretador/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcas['10']", is("PRETO")))
                .andExpect(jsonPath("$.marcas['20']", is("BRANCO")))
                .andExpect(jsonPath("$.marcas['30']", is("AZUL")))
                .andExpect(jsonPath("$.marcas['40']", is("VERMELHO")))
                .andExpect(jsonPath("$.marcas['50']", is("AMARELO")))
                .andExpect(jsonPath("$.marcas['60']", is("CINZA")))
                .andExpect(jsonPath("$.cargos['100']", is("VENDEDOR LOJA")))
                .andExpect(jsonPath("$.cargosDetalhados", hasSize(5)))
                .andExpect(jsonPath("$.lojas['1']", is("LOJA-1")))
                .andExpect(jsonPath("$.lojas['80']", is("LOJA-80")))
                .andExpect(jsonPath("$.faixaMatriculas", is("MATRIC-1 a MATRIC-600")))
                .andExpect(jsonPath("$.taxasContratuaisBase", hasSize(30)));
    }
}
