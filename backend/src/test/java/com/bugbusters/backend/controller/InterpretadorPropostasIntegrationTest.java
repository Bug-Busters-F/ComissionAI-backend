package com.bugbusters.backend.controller;

import com.bugbusters.backend.service.client.AiServiceClient;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.test.web.client.match.MockRestRequestMatchers;
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
@TestPropertySource(properties = "ai.service.url=http://localhost:8000")
class InterpretadorPropostasIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RestClient.Builder aiRestClientBuilder;

    @Autowired
    private AiServiceClient aiServiceClient;

    @Autowired
    private com.bugbusters.backend.brand.BrandRepository brandRepository;

    @Autowired
    private com.bugbusters.backend.position.PositionRepository positionRepository;

    @Autowired
    private com.bugbusters.backend.basecomiss.BaseComissRepository baseComissRepository;

    private MockMvc mockMvc;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockServer = MockRestServiceServer.bindTo(aiRestClientBuilder).build();
        aiServiceClient.setAiRestClient(aiRestClientBuilder.build());

        var brand = brandRepository.findByCode(10).orElseGet(() -> {
            var b = new com.bugbusters.backend.brand.Brand();
            b.setCode(10);
            b.setDescription("PRETO");
            return brandRepository.save(b);
        });

        var pos = positionRepository.findByCode(150).orElseGet(() -> {
            var p = new com.bugbusters.backend.position.Position();
            p.setCode(150);
            p.setDescription("GERENTE DE LOJA");
            return positionRepository.save(p);
        });

        if (baseComissRepository.findByBrandCodeAndPositionCodeAndReferenceMonth(10, 150, LocalDate.of(2026, 10, 1)).isEmpty()) {
            baseComissRepository.save(new com.bugbusters.backend.basecomiss.BaseComiss(
                    brand, pos, new BigDecimal("0.0250"), LocalDate.of(2026, 10, 1)
            ));
        }
    }

    @Test
    @DisplayName("Deve extrair múltiplas propostas de regras com faixas e ajustes sobre a base")
    void deveExtrairMultiplasPropostasComSucesso() throws Exception {
        // Resposta do agente (/api/v2/interpretar): ele separa as regras e resolve o cadastro, mas deixa o
        // ajuste sobre a taxa base sem taxa e com pendência; o backend calcula o ajuste a partir do trecho.
        String respostaAgente = """
            {
              "status": "PROPOSTA_COM_PENDENCIAS",
              "propostas": [
                {
                  "idLocal": "p1",
                  "condicoes": {"canal": "ECOMMERCE", "codMarca": 10, "descrMarca": "PRETO"},
                  "taxa": "0.0500",
                  "dataInicio": "2026-10-01", "dataFim": "2026-10-31",
                  "origem": {"trecho": "Pagar 5% no canal ecommerce durante outubro"},
                  "referencias": [{"fonte": "CADASTRO", "chave": {"marca": 10}}],
                  "confianca": "0.95",
                  "pendencias": []
                },
                {
                  "idLocal": "p2",
                  "condicoes": {"codMarca": 10, "codCargo": 150, "codLoja": 75, "faixaValor": {"campo": "VENDA", "min": "5000.00", "max": null, "minInclusivo": false, "maxInclusivo": false}},
                  "taxa": null,
                  "dataInicio": "2026-10-01", "dataFim": "2026-10-31",
                  "origem": {"trecho": "Para a loja 75, pagar taxa base + 1.5% em vendas acima de R$ 5.000."},
                  "referencias": [{"fonte": "CADASTRO", "chave": {"loja": 75}}],
                  "confianca": "0.80",
                  "pendencias": [{"codigo": "CAMPO_AUSENTE", "campo": "taxa",
                                  "mensagem": "Ajuste sobre a taxa base ('taxa base + 1.5%') depende da comissão base; a taxa final ainda não foi calculada."}]
                }
              ],
              "pendenciasGerais": [],
              "trechosNaoMapeados": [],
              "trilhaFerramentas": []
            }
            """;

        mockServer.expect(requestTo(endsWith("/api/v2/interpretar")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(respostaAgente, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "texto": "1. Pagar 5% no canal ecommerce durante outubro.\\n2. Para a loja 75, pagar taxa base + 1.5% em vendas acima de R$ 5.000.",
                                "contexto": {
                                    "vigenciaCampanha": {"inicio": "2026-10-01", "fim": "2026-10-31"}
                                }
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadeBlocos", is(2)))
                .andExpect(jsonPath("$.propostas", hasSize(2)))
                .andExpect(jsonPath("$.propostas[0].blocoId", is("p1")))
                .andExpect(jsonPath("$.propostas[0].filtros.canal", is("ECOMMERCE")))
                .andExpect(jsonPath("$.propostas[0].taxaFinal", is(0.0500)))
                .andExpect(jsonPath("$.propostas[0].origensPorCampo.marca", is("BASE_DADOS")))
                .andExpect(jsonPath("$.propostas[0].origensPorCampo.dataInicio", is("CONTEXTO")))
                .andExpect(jsonPath("$.propostas[1].blocoId", is("p2")))
                .andExpect(jsonPath("$.propostas[1].filtros.codLoja", is(75)))
                .andExpect(jsonPath("$.propostas[1].condicaoValor.valorMinimo", is(5000.0)))
                .andExpect(jsonPath("$.propostas[1].condicaoValor.minInclusivo", is(false)))
                .andExpect(jsonPath("$.propostas[1].operacaoBase.tipoOperacao", is("ACRESCIMO_PONTOS")))
                .andExpect(jsonPath("$.propostas[1].taxaFinal", notNullValue()))
                .andExpect(jsonPath("$.propostas[1].pendencias[?(@.campo == 'taxa')]", hasSize(0)))
                .andExpect(jsonPath("$.propostas[1].explicacao", notNullValue()))
                .andExpect(jsonPath("$.propostas[1].pythonEquivalente", containsString("def calcular_comissao")));
        mockServer.verify();
    }

    private static final String PROPOSTAS_ATUAIS = """
            [
                {
                    "blocoId": "bloco-1",
                    "trechoOrigem": "Loja 75 pagar 5%",
                    "filtros": { "codLoja": 75 },
                    "condicaoValor": { "valorMinimo": 5000.00, "minInclusivo": false },
                    "operacaoBase": { "tipoOperacao": "DEFINIR_TAXA", "valorAjuste": 0.0500 },
                    "taxaFinal": 0.0500,
                    "vigencia": { "dataInicio": "2026-10-01", "dataFim": "2026-10-31" }
                },
                {
                    "blocoId": "bloco-2",
                    "trechoOrigem": "Marca PRETO ganha 1 ponto a mais",
                    "filtros": { "codMarca": 10, "descrMarca": "PRETO", "codCargo": 150, "descriCargo": "GERENTE DE LOJA" },
                    "operacaoBase": {
                        "tipoOperacao": "ACRESCIMO_PONTOS", "valorAjuste": 0.0100,
                        "referenciaBase": { "tipoBase": "BASE_COMISS", "taxaBaseConsultada": 0.0250, "descricao": "Base PRETO" }
                    },
                    "taxaFinal": 0.0350,
                    "vigencia": { "dataInicio": "2026-10-01", "dataFim": "2026-10-31" }
                }
            ]
            """;

    @Test
    @DisplayName("Deve reinterpretar bloco pelo agente (/api/v2/reinterpretar) preservando os demais blocos")
    void deveReinterpretarBlocoEspecifico() throws Exception {
        String respostaAgente = """
            {
              "propostasSubstituicao": [
                {"idLocal": "bloco-1",
                 "condicoes": {"codLoja": 75, "faixaValor": {"campo": "VENDA", "min": "10000.00", "max": null, "minInclusivo": false, "maxInclusivo": false}},
                 "operacao": {"tipo": "TAXA_ABSOLUTA", "valor": "0.0500"}, "taxa": "0.0500",
                 "dataInicio": "2026-10-01", "dataFim": "2026-10-31",
                 "origem": {"trecho": "vendas acima de R$ 10.000"},
                 "explicacao": "Explicação do agente para o bloco 1.",
                 "codigoPython": "# codigo do agente\\nREGRA = {}",
                 "pendencias": []}
              ],
              "camposAlterados": ["condicoes.faixaValor"],
              "pendenciasGerais": []
            }
            """;

        mockServer.expect(requestTo(endsWith("/api/v2/reinterpretar")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.jsonPath("$.idLocal").value("bloco-1"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.textoAlterado").value("Mudar a condição para vendas acima de R$ 10.000"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.blocoAtual.condicoes.codLoja").value(75))
                .andExpect(MockRestRequestMatchers.jsonPath("$.blocoAtual.operacao.tipo").value("TAXA_ABSOLUTA"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.demaisBlocos.length()").value(1))
                .andExpect(MockRestRequestMatchers.jsonPath("$.demaisBlocos[0].idLocal").value("bloco-2"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.demaisBlocos[0].operacao.tipo").value("ADICIONAR_PP"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.demaisBlocos[0].referencias[0].taxaBase").value(0.0250))
                .andExpect(MockRestRequestMatchers.jsonPath("$.contexto.campanhaId").value("7"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.contexto.origem").value("VERSAO_EXISTENTE"))
                .andRespond(withSuccess(respostaAgente, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/reinterpretar-bloco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "blocoId": "bloco-1",
                                "instrucaoAjuste": "Mudar a condição para vendas acima de R$ 10.000",
                                "propostasAtuais": %s,
                                "contexto": {"campanhaId": "7", "origem": "VERSAO_EXISTENTE"}
                            }
                            """.formatted(PROPOSTAS_ATUAIS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocoModificadoId", is("bloco-1")))
                .andExpect(jsonPath("$.resumoModificacao", containsString("condicoes.faixaValor")))
                .andExpect(jsonPath("$.propostasAtualizadas", hasSize(2)))
                .andExpect(jsonPath("$.propostasAtualizadas[0].blocoId", is("bloco-1")))
                .andExpect(jsonPath("$.propostasAtualizadas[0].condicaoValor.valorMinimo", is(10000.0)))
                .andExpect(jsonPath("$.propostasAtualizadas[0].explicacao", is("Explicação do agente para o bloco 1.")))
                .andExpect(jsonPath("$.propostasAtualizadas[0].pythonEquivalente", containsString("# codigo do agente")))
                .andExpect(jsonPath("$.propostasAtualizadas[1].blocoId", is("bloco-2")))
                .andExpect(jsonPath("$.propostasAtualizadas[1].taxaFinal", is(0.0350)));
        mockServer.verify();
    }

    @Test
    @DisplayName("Reinterpretação que divide a regra em várias substitui o bloco alvo no mesmo lugar, com ids únicos")
    void deveReinterpretarBlocoDividindoEmVariasRegras() throws Exception {
        String respostaAgente = """
            {
              "propostasSubstituicao": [
                {"idLocal": "bloco-1", "condicoes": {"codLoja": 10}, "operacao": {"tipo": "TAXA_ABSOLUTA", "valor": "0.0200"},
                 "taxa": "0.0200", "dataInicio": "2026-10-01", "dataFim": "2026-10-31", "origem": {"trecho": "2% para a loja 10"}},
                {"idLocal": "bloco-1_2", "condicoes": {"codLoja": 20}, "operacao": {"tipo": "TAXA_ABSOLUTA", "valor": "0.0400"},
                 "taxa": "0.0400", "dataInicio": "2026-10-01", "dataFim": "2026-10-31", "origem": {"trecho": "4% para a loja 20"}}
              ],
              "camposAlterados": ["condicoes.codLoja", "taxa"],
              "pendenciasGerais": [{"codigo": "TOOL_FALHA", "campo": "geral", "mensagem": "Consulta ao cadastro falhou."}]
            }
            """;
        mockServer.expect(requestTo(endsWith("/api/v2/reinterpretar")))
                .andRespond(withSuccess(respostaAgente, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/reinterpretar-bloco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"blocoId": "bloco-1", "instrucaoAjuste": "2%% para a loja 10 e 4%% para a loja 20",
                             "propostasAtuais": %s}
                            """.formatted(PROPOSTAS_ATUAIS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumoModificacao", containsString("dividido em 2 regras")))
                .andExpect(jsonPath("$.propostasAtualizadas", hasSize(3)))
                .andExpect(jsonPath("$.propostasAtualizadas[0].blocoId", is("bloco-1")))
                .andExpect(jsonPath("$.propostasAtualizadas[0].filtros.codLoja", is(10)))
                .andExpect(jsonPath("$.propostasAtualizadas[0].pendencias[?(@.codigo == 'TOOL_FALHA')]", hasSize(1)))
                .andExpect(jsonPath("$.propostasAtualizadas[1].blocoId", is("bloco-1_2")))
                .andExpect(jsonPath("$.propostasAtualizadas[1].filtros.codLoja", is(20)))
                .andExpect(jsonPath("$.propostasAtualizadas[2].blocoId", is("bloco-2")));
        mockServer.verify();
    }

    @Test
    @DisplayName("Reinterpretação sem propostas de substituição do agente é resposta inválida (502)")
    void deveRejeitarReinterpretacaoSemSubstituicao() throws Exception {
        mockServer.expect(requestTo(endsWith("/api/v2/reinterpretar")))
                .andRespond(withSuccess("{\"propostasSubstituicao\": []}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/reinterpretar-bloco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"blocoId": "bloco-1", "instrucaoAjuste": "qualquer", "propostasAtuais": %s}
                            """.formatted(PROPOSTAS_ATUAIS)))
                .andExpect(status().is5xxServerError());
        mockServer.verify();
    }

    private static final String ATUALIZAR_PROPOSTA = """
            {
                "proposta": {
                    "blocoId": "bloco-1",
                    "trechoOrigem": "Vendas no e-commerce",
                    "filtros": { "canal": "ECOMMERCE", "codMarca": 10, "descrMarca": "PRETO" },
                    "condicaoValor": { "valorMinimo": 2000.00, "minInclusivo": false },
                    "operacaoBase": { "tipoOperacao": "DEFINIR_TAXA", "valorAjuste": 0.0600 },
                    "taxaFinal": 0.0600,
                    "vigencia": { "dataInicio": "2026-10-01", "dataFim": "2026-10-31" }
                }
            }
            """;

    @Test
    @DisplayName("Deve atualizar artefatos explicativos pelo agente (/api/v2/atualizar-artefatos) após edição manual")
    void deveAtualizarArtefatosAposEdicaoManual() throws Exception {
        mockServer.expect(requestTo(endsWith("/api/v2/atualizar-artefatos")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.jsonPath("$.bloco.idLocal").value("bloco-1"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.bloco.taxa").value(0.0600))
                .andExpect(MockRestRequestMatchers.jsonPath("$.bloco.operacao.tipo").value("TAXA_ABSOLUTA"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.bloco.condicoes.canal").value("ECOMMERCE"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.bloco.condicoes.faixaValor.min").value(2000.00))
                .andExpect(MockRestRequestMatchers.jsonPath("$.bloco.condicoes.faixaValor.minInclusivo").value(false))
                .andExpect(MockRestRequestMatchers.jsonPath("$.bloco.dataInicio").value("2026-10-01"))
                .andRespond(withSuccess("""
                        {"bloco": {"idLocal": "bloco-1", "taxa": "0.0600",
                                   "explicacao": "Taxa de 6,00% definida pelo gestor.",
                                   "codigoPython": "REGRA = {'taxa': Decimal('0.0600')}"}}
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/atualizar-artefatos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ATUALIZAR_PROPOSTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocoId", is("bloco-1")))
                .andExpect(jsonPath("$.taxaCalculada", is(0.0600)))
                .andExpect(jsonPath("$.explicacao", is("Taxa de 6,00% definida pelo gestor.")))
                .andExpect(jsonPath("$.pythonEquivalente", containsString("Decimal('0.0600')")))
                .andExpect(jsonPath("$.completa", is(true)));
        mockServer.verify();
    }

    @Test
    @DisplayName("Se o agente estiver indisponível, atualizar artefatos usa a geração local e não bloqueia a edição")
    void deveAtualizarArtefatosLocalmenteQuandoAgenteIndisponivel() throws Exception {
        mockServer.expect(requestTo(endsWith("/api/v2/atualizar-artefatos")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        mockMvc.perform(post("/api/v1/interpretador/atualizar-artefatos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ATUALIZAR_PROPOSTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxaCalculada", is(0.0600)))
                .andExpect(jsonPath("$.explicacao", containsString("6.00%")))
                .andExpect(jsonPath("$.explicacao", containsString("ECOMMERCE")))
                .andExpect(jsonPath("$.pythonEquivalente", containsString("0.0600")))
                .andExpect(jsonPath("$.completa", is(true)));
        mockServer.verify();
    }

    @Test
    @DisplayName("Interpretação usa operação, taxa, explicação e Python devolvidos pelo agente (ajuste relativo sobre a base)")
    void deveUsarOperacaoETaxaDoAgente() throws Exception {
        String respostaAgente = """
            {
              "status": "PROPOSTA_COMPLETA",
              "propostas": [
                {"idLocal": "p1",
                 "condicoes": {"codMarca": 10, "descrMarca": "PRETO", "codCargo": 150, "descriCargo": "GERENTE DE LOJA"},
                 "operacao": {"tipo": "AUMENTAR_PERCENTUAL", "valor": "0.0100"}, "taxa": "0.0253",
                 "dataInicio": "2026-10-01", "dataFim": "2026-10-31",
                 "origem": {"trecho": "aumentar a comissão em 1% sobre a base"},
                 "referencias": [{"fonte": "BASE_COMISS", "chave": {"codMarca": 10, "codCargo": 150, "competencia": "2026-10"}, "taxaBase": "0.0250"}],
                 "explicacao": "Explicação do agente.", "codigoPython": "REGRA = {'taxa': Decimal('0.0253')}",
                 "pendencias": []},
                {"idLocal": "p2",
                 "condicoes": {"codMarca": 10, "codCargo": 150},
                 "operacao": {"tipo": "ADICIONAR_PP", "valor": "0.0100"}, "taxa": null,
                 "dataInicio": "2026-10-01", "dataFim": "2026-10-31",
                 "origem": {"trecho": "mais 1 ponto sobre a base"},
                 "pendencias": [{"codigo": "BASE_AUSENTE", "campo": "taxa", "mensagem": "Não há base de comissão para a competência."}]}
              ]
            }
            """;
        mockServer.expect(requestTo(endsWith("/api/v2/interpretar")))
                .andRespond(withSuccess(respostaAgente, MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/interpretador/extrair-propostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\": \"pedido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propostas[0].operacaoBase.tipoOperacao", is("MULTIPLICADOR_BASE")))
                .andExpect(jsonPath("$.propostas[0].operacaoBase.valorAjuste", is(1.01)))
                .andExpect(jsonPath("$.propostas[0].operacaoBase.referenciaBase.taxaBaseConsultada", is(0.0250)))
                .andExpect(jsonPath("$.propostas[0].taxaFinal", is(0.0253)))
                .andExpect(jsonPath("$.propostas[0].referenciasConsultadas[?(@.tipo == 'BASE_COMISS')]", hasSize(1)))
                .andExpect(jsonPath("$.propostas[0].explicacao", is("Explicação do agente.")))
                .andExpect(jsonPath("$.propostas[0].pythonEquivalente", containsString("Decimal('0.0253')")))
                .andExpect(jsonPath("$.propostas[1].operacaoBase.tipoOperacao", is("ACRESCIMO_PONTOS")))
                .andExpect(jsonPath("$.propostas[1].taxaFinal").doesNotExist())
                .andExpect(jsonPath("$.propostas[1].pendencias[?(@.codigo == 'BASE_AUSENTE')]", hasSize(1)))
                .andExpect(jsonPath("$.propostas[1].completa", is(false)));
        mockServer.verify();
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
