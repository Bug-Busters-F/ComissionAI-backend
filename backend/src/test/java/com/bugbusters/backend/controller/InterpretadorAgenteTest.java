package com.bugbusters.backend.controller;

import static org.hamcrest.Matchers.contains;
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

    @Test
    @DisplayName("1a. Resposta com propostas vazias resulta em todasCompletas false e pendência impeditiva NENHUMA_PROPOSTA")
    void deveTratarRespostaComListaDePropostasVazia() throws Exception {
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess("{\"status\":\"SEM_PROPOSTA\",\"propostas\":[],\"pendenciasGerais\":[]}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"texto qualquer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas", hasSize(0)))
                .andExpect(jsonPath("$.todasCompletas", is(false)))
                .andExpect(jsonPath("$.possuiPendencias", is(true)))
                .andExpect(jsonPath("$.pendenciasGerais[0].codigo", is("NENHUMA_PROPOSTA")))
                .andExpect(jsonPath("$.pendenciasGerais[0].campo", is("texto")))
                .andExpect(jsonPath("$.pendenciasGerais[0].severidade", is("IMPEDIMENTO")));
        mockServer.verify();
    }

    @Test
    @DisplayName("1b. Pendências gerais como TOOL_FALHA viram impeditivas e tornam todasCompletas false")
    void deveClassificarToolFalhaComoImpeditivaEmPendenciasGerais() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {
                  "idLocal": "p1",
                  "taxa": "0.0500",
                  "dataInicio": "2026-10-01",
                  "dataFim": "2026-10-31",
                  "origem": {"trecho": "5% em outubro"},
                  "pendencias": []
                }
              ],
              "pendenciasGerais": [
                {"codigo": "TOOL_FALHA", "campo": "geral", "mensagem": "Falha na execução de ferramenta."}
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"5% em outubro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas", hasSize(1)))
                .andExpect(jsonPath("$.propostas[0].completa", is(true)))
                .andExpect(jsonPath("$.pendenciasGerais[0].codigo", is("TOOL_FALHA")))
                .andExpect(jsonPath("$.pendenciasGerais[0].severidade", is("IMPEDIMENTO")))
                .andExpect(jsonPath("$.todasCompletas", is(false)));
        mockServer.verify();
    }

    @Test
    @DisplayName("1c. Pendência geral VIGENCIA_INCOMPATIVEL permanece como aviso")
    void deveManterVigenciaIncompativelComoAvisoEmPendenciasGerais() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {
                  "idLocal": "p1",
                  "taxa": "0.0500",
                  "dataInicio": "2026-10-01",
                  "dataFim": "2026-10-31",
                  "origem": {"trecho": "5% em outubro"},
                  "pendencias": []
                }
              ],
              "pendenciasGerais": [
                {"codigo": "VIGENCIA_INCOMPATIVEL", "campo": "vigencia", "mensagem": "Vigência ajustada."}
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"5% em outubro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendenciasGerais[0].codigo", is("VIGENCIA_INCOMPATIVEL")))
                .andExpect(jsonPath("$.pendenciasGerais[0].severidade", is("AVISO")))
                .andExpect(jsonPath("$.todasCompletas", is(true)));
        mockServer.verify();
    }

    @Test
    @DisplayName("2a. Agente sem datas e contexto com vigência herda vigência da campanha e origem CONTEXTO")
    void deveHerdarVigenciaDaCampanhaQuandoAgenteNaoEnviaDatas() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {
                  "idLocal": "p1",
                  "taxa": "0.0500",
                  "dataInicio": null,
                  "dataFim": null,
                  "origem": {"trecho": "5% para vendedores"},
                  "pendencias": []
                }
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        String corpo = """
            {
              "texto": "5% para vendedores",
              "contexto": {
                "vigenciaCampanha": {"inicio": "2026-11-01", "fim": "2026-11-30"}
              }
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas[0].vigencia.dataInicio", is("2026-11-01")))
                .andExpect(jsonPath("$.propostas[0].vigencia.dataFim", is("2026-11-30")))
                .andExpect(jsonPath("$.propostas[0].origensPorCampo.dataInicio", is("CONTEXTO")))
                .andExpect(jsonPath("$.propostas[0].origensPorCampo.dataFim", is("CONTEXTO")))
                .andExpect(jsonPath("$.propostas[0].pendencias[?(@.codigo == 'VIGENCIA_INCOMPATIVEL')]", hasSize(0)));
        mockServer.verify();
    }

    @Test
    @DisplayName("2b. Agente com datas diferentes resulta na vigência da campanha e em um único aviso VIGENCIA_INCOMPATIVEL")
    void deveSubstituirDatasDiferentesPelaVigenciaDaCampanhaEGerarAviso() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {
                  "idLocal": "p1",
                  "taxa": "0.0500",
                  "dataInicio": "2026-12-01",
                  "dataFim": "2026-12-31",
                  "origem": {"trecho": "5% em dezembro"},
                  "pendencias": []
                }
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        String corpo = """
            {
              "texto": "5% em dezembro",
              "contexto": {
                "vigenciaCampanha": {"inicio": "2026-10-01", "fim": "2026-10-31"}
              }
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas[0].vigencia.dataInicio", is("2026-10-01")))
                .andExpect(jsonPath("$.propostas[0].vigencia.dataFim", is("2026-10-31")))
                .andExpect(jsonPath("$.propostas[0].origensPorCampo.dataInicio", is("CONTEXTO")))
                .andExpect(jsonPath("$.propostas[0].origensPorCampo.dataFim", is("CONTEXTO")))
                .andExpect(jsonPath("$.propostas[0].pendencias[?(@.codigo == 'VIGENCIA_INCOMPATIVEL')]", hasSize(1)))
                .andExpect(jsonPath("$.propostas[0].pendencias[?(@.codigo == 'VIGENCIA_INCOMPATIVEL')].severidade", contains("AVISO")))
                .andExpect(jsonPath("$.propostas[0].pendencias[?(@.codigo == 'VIGENCIA_INCOMPATIVEL')].campo", contains("vigencia")))
                .andExpect(jsonPath("$.propostas[0].explicacao", containsString("01/10/2026")))
                .andExpect(jsonPath("$.propostas[0].pythonEquivalente", containsString("2026-10-01")));
        mockServer.verify();
    }

    @Test
    @DisplayName("2c. Sem vigência no contexto, as datas do agente são preservadas com origem TEXTO")
    void deveUsarDatasDoAgenteQuandoNaoHaVigenciaNoContexto() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {
                  "idLocal": "p1",
                  "taxa": "0.0500",
                  "dataInicio": "2026-10-01",
                  "dataFim": "2026-10-31",
                  "origem": {"trecho": "5% em outubro"},
                  "pendencias": []
                }
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"5% em outubro\",\"contexto\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas[0].vigencia.dataInicio", is("2026-10-01")))
                .andExpect(jsonPath("$.propostas[0].vigencia.dataFim", is("2026-10-31")))
                .andExpect(jsonPath("$.propostas[0].origensPorCampo.dataInicio", is("TEXTO")))
                .andExpect(jsonPath("$.propostas[0].origensPorCampo.dataFim", is("TEXTO")));
        mockServer.verify();
    }

    @Test
    @DisplayName("3a. Faixa de valor com minInclusivo e maxInclusivo como string converte para boolean sem erro")
    void deveConverterLimitesInclusivosEmFormatoString() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {
                  "idLocal": "p1",
                  "condicoes": {
                    "faixaValor": {
                      "min": "1000.00",
                      "minInclusivo": "true",
                      "max": "5000.00",
                      "maxInclusivo": "false"
                    }
                  },
                  "taxa": "0.0300",
                  "origem": {"trecho": "3% entre 1000 e 5000"},
                  "pendencias": []
                }
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"3% entre 1000 e 5000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas[0].condicaoValor.valorMinimo", is(1000.0)))
                .andExpect(jsonPath("$.propostas[0].condicaoValor.minInclusivo", is(true)))
                .andExpect(jsonPath("$.propostas[0].condicaoValor.valorMaximo", is(5000.0)))
                .andExpect(jsonPath("$.propostas[0].condicaoValor.maxInclusivo", is(false)));
        mockServer.verify();
    }

    @Test
    @DisplayName("3b. Resposta nula da IA resulta em HTTP 502 Bad Gateway com mensagem adequada")
    void deveRetornar502QuandoRespostaForNula() throws Exception {
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"pedido\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status", is(502)));
        mockServer.verify();
    }

    @Test
    @DisplayName("3c. Campo propostas como string resulta em HTTP 502 Bad Gateway")
    void deveRetornar502QuandoPropostasNaoForLista() throws Exception {
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess("{\"status\":\"OK\",\"propostas\":\"invalido\"}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"pedido\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status", is(502)));
        mockServer.verify();
    }

    @Test
    @DisplayName("3d. Um bloco válido e um malformado resultam em 1 proposta e na pendência impeditiva BLOCO_INVALIDO")
    void deveIsolarBlocoMalformadoSemDerrubarDemaisBlocos() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COM_PENDENCIAS",
              "propostas": [
                {
                  "idLocal": "bloco-valido",
                  "taxa": "0.0400",
                  "origem": {"trecho": "4% valido"},
                  "pendencias": []
                },
                "bloco-malformado-em-formato-de-string"
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"texto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas", hasSize(1)))
                .andExpect(jsonPath("$.propostas[0].blocoId", is("bloco-valido")))
                .andExpect(jsonPath("$.pendenciasGerais[?(@.codigo == 'BLOCO_INVALIDO')]", hasSize(1)))
                .andExpect(jsonPath("$.pendenciasGerais[?(@.codigo == 'BLOCO_INVALIDO')].severidade", contains("IMPEDIMENTO")))
                .andExpect(jsonPath("$.todasCompletas", is(false)));
        mockServer.verify();
    }

    @Test
    @DisplayName("4a. Bloco com identificador do agente mantém o ID informado")
    void devePreservarIdDoAgente() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {
                  "idLocal": "regra-custom-123",
                  "taxa": "0.0500",
                  "origem": {"trecho": "5%"},
                  "pendencias": []
                }
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"5%\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas[0].blocoId", is("regra-custom-123")));
        mockServer.verify();
    }

    @Test
    @DisplayName("4b. Bloco sem identificador do agente recebe id gerado no formato bloco-N")
    void deveGerarBlocoIdQuandoAgenteNaoInformar() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {
                  "taxa": "0.0500",
                  "origem": {"trecho": "5%"},
                  "pendencias": []
                }
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"5%\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas[0].blocoId", is("bloco-1")));
        mockServer.verify();
    }

    @Test
    @DisplayName("4c. Identificadores repetidos na resposta do agente não geram duplicidade")
    void deveResolverIdentificadoresRepetidosSemDuplicidade() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {
                  "idLocal": "mesmo-id",
                  "taxa": "0.0300",
                  "origem": {"trecho": "3%"},
                  "pendencias": []
                },
                {
                  "idLocal": "mesmo-id",
                  "taxa": "0.0400",
                  "origem": {"trecho": "4%"},
                  "pendencias": []
                }
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"3% e 4%\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas", hasSize(2)))
                .andExpect(jsonPath("$.propostas[0].blocoId", is("bloco-1")))
                .andExpect(jsonPath("$.propostas[1].blocoId", is("bloco-2")));
        mockServer.verify();
    }

    @Test
    @DisplayName("5. Duas pendências com a mesma mensagem mas campos diferentes são mantidas separadamente")
    void deveDeduplicarPendenciasPorCampoCodigoEMensagem() throws Exception {
        String resposta = """
            {
              "status": "PROPOSTA_COM_PENDENCIAS",
              "propostas": [
                {
                  "idLocal": "p1",
                  "taxa": "0.0500",
                  "origem": {"trecho": "5%"},
                  "pendencias": [
                    {
                      "campo": "codCargo",
                      "codigo": "CAMPO_INVALIDO",
                      "mensagem": "Dado não reconhecido no cadastro."
                    },
                    {
                      "campo": "codLoja",
                      "codigo": "CAMPO_INVALIDO",
                      "mensagem": "Dado não reconhecido no cadastro."
                    }
                  ]
                }
              ]
            }
            """;
        mockServer.expect(requestTo(containsString("/api/v2/interpretar")))
                .andRespond(withSuccess(resposta, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"5%\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas[0].pendencias[?(@.campo == 'codCargo')]", hasSize(1)))
                .andExpect(jsonPath("$.propostas[0].pendencias[?(@.campo == 'codLoja')]", hasSize(1)));
        mockServer.verify();
    }
}
