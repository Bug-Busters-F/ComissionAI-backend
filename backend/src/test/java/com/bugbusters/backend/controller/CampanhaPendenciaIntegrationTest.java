package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.esteira.AvancarEsteiraRequest;
import com.bugbusters.backend.dto.pendencia.AceitarPendenciaRequest;
import com.bugbusters.backend.dto.pendencia.RevogarAceitePendenciaRequest;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.AtualizarProgramaPythonRequest;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.CampanhaVersao;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class CampanhaPendenciaIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private RegraRepository regraRepository;

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private Long criarCampanhaComPendenciaImpeditiva() throws Exception {
        String payload = """
            {
              "titulo": "Campanha Black Friday com Pendência",
              "textoOriginal": "Vendas no canal LOJA_FISICA: comissão de 3.5%",
              "estado": "DRAFT",
              "dataInicio": "2026-11-01",
              "dataFim": "2026-11-30",
              "regras": [
                {
                  "blocoId": "regra-pendente-1",
                  "nome": "Regra Loja 999",
                  "canal": "LOJA_FISICA",
                  "taxa": 0.035,
                  "valorAjuste": 0.035,
                  "tipoOperacao": "DEFINIR_TAXA",
                  "completa": false,
                  "rastreabilidade": {
                    "origensPorCampo": { "canal": "TEXTO", "taxa": "EDICAO_MANUAL" },
                    "camposManuais": ["taxaPercentual"],
                    "publicoTodasVendas": false,
                    "artefatosAtualizados": true,
                    "referenciasConsultadas": [],
                    "pendencias": [
                      {
                        "id": "pendencia:loja:999",
                        "campo": "filtros.codLoja",
                        "codigo": "LOJA_FORA_CATALOGO",
                        "mensagem": "Loja 999 não encontrada na base cadastral homologada.",
                        "severidade": "IMPEDIMENTO"
                      }
                    ],
                    "pendenciasCampanha": []
                  }
                }
              ]
            }
            """;

        String response = mvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode tree = json.readTree(response);
        return tree.path("id").asLong();
    }

    @Test
    @DisplayName("BUG-102: Deve registrar aceite formal e rastreável de pendência e desbloquear a regra")
    void deveRegistrarAceiteRastreavelDePendenciaComSucesso() throws Exception {
        Long campanhaId = criarCampanhaComPendenciaImpeditiva();

        // 1. Verifica estado inicial das pendências
        mvc.perform(get("/api/v1/campanhas/{id}/pendencias", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPendencias", is(1)))
                .andExpect(jsonPath("$.totalImpedimentos", is(1)))
                .andExpect(jsonPath("$.totalAceitas", is(0)))
                .andExpect(jsonPath("$.totalPendentesImpeditivas", is(1)))
                .andExpect(jsonPath("$.aptaParaAvanco", is(false)))
                .andExpect(jsonPath("$.pendencias[0].id", is("pendencia:loja:999")))
                .andExpect(jsonPath("$.pendencias[0].aceita", is(false)));

        // 2. Registra o aceite rastreável com justificativa
        AceitarPendenciaRequest request = new AceitarPendenciaRequest(
                null,
                "pendencia:loja:999",
                "LOJA_FORA_CATALOGO",
                "filtros.codLoja",
                "Loja 999 não encontrada na base cadastral homologada.",
                com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia.IMPEDIMENTO,
                "gestor.davi@empresa.com",
                "Loja inaugurada na semana corrente; inclusão formal em andamento no ERP."
        );

        mvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.pendenciaIdentificador", is("pendencia:loja:999")))
                .andExpect(jsonPath("$.codigo", is("LOJA_FORA_CATALOGO")))
                .andExpect(jsonPath("$.status", is("ACEITA")))
                .andExpect(jsonPath("$.aceitoPor", is("gestor.davi@empresa.com")))
                .andExpect(jsonPath("$.motivoAceite", containsString("Loja inaugurada na semana corrente")))
                .andExpect(jsonPath("$.aceitoEm", notNullValue()));

        // 3. Verifica resumo atualizado após o aceite
        mvc.perform(get("/api/v1/campanhas/{id}/pendencias", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAceitas", is(1)))
                .andExpect(jsonPath("$.totalPendentesImpeditivas", is(0)))
                .andExpect(jsonPath("$.aptaParaAvanco", is(true)))
                .andExpect(jsonPath("$.pendencias[0].aceita", is(true)))
                .andExpect(jsonPath("$.pendencias[0].aceite.aceitoPor", is("gestor.davi@empresa.com")));

        // 4. Verifica se a regra foi desbloqueada (completa = true)
        Campanha campanha = campanhaRepository.findById(campanhaId).orElseThrow();
        CampanhaVersao versao = campanha.versaoParaRevisao();
        List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
        assertThat(regras).isNotEmpty();
        assertThat(regras.get(0).getCompleta()).isTrue();
    }

    @Test
    @DisplayName("BUG-102: Deve rejeitar aceite sem justificativa formal ou responsável")
    void deveRejeitarAceiteSemJustificativaOuResponsavel() throws Exception {
        Long campanhaId = criarCampanhaComPendenciaImpeditiva();

        AceitarPendenciaRequest requestInvalido = new AceitarPendenciaRequest(
                null,
                "pendencia:loja:999",
                "LOJA_FORA_CATALOGO",
                "filtros.codLoja",
                "Mensagem",
                null,
                "", // Aceito por vazio
                "   " // Motivo vazio
        );

        mvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BUG-102: Deve revogar formalmente um aceite de pendência e rebloquear a regra")
    void deveRevogarAceiteDePendenciaERebloquearRegra() throws Exception {
        Long campanhaId = criarCampanhaComPendenciaImpeditiva();

        // 1. Registra aceite
        AceitarPendenciaRequest aceiteReq = new AceitarPendenciaRequest(
                null,
                "pendencia:loja:999",
                "LOJA_FORA_CATALOGO",
                "filtros.codLoja",
                "Mensagem",
                null,
                "gestor.davi@empresa.com",
                "Risco assumido temporariamente."
        );

        String aceiteRes = mvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(aceiteReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long aceiteId = json.readTree(aceiteRes).path("id").asLong();

        // 2. Revoga o aceite
        RevogarAceitePendenciaRequest revogarReq = new RevogarAceitePendenciaRequest(
                "compliance.auditor@empresa.com",
                "Revisão de risco reprovada pelo comitê de compliance."
        );

        mvc.perform(post("/api/v1/campanhas/{id}/pendencias/{aceiteId}/revogar", campanhaId, aceiteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(revogarReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REVOGADA")))
                .andExpect(jsonPath("$.revogadoPor", is("compliance.auditor@empresa.com")))
                .andExpect(jsonPath("$.motivoRevogacao", containsString("Revisão de risco reprovada")))
                .andExpect(jsonPath("$.revogadoEm", notNullValue()));

        // 3. Verifica resumo após revogação
        mvc.perform(get("/api/v1/campanhas/{id}/pendencias", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAceitas", is(0)))
                .andExpect(jsonPath("$.totalPendentesImpeditivas", is(1)))
                .andExpect(jsonPath("$.aptaParaAvanco", is(false)))
                .andExpect(jsonPath("$.pendencias[0].aceita", is(false)));

        // 4. Verifica se a regra voltou a ser incompleta (completa = false)
        Campanha campanha = campanhaRepository.findById(campanhaId).orElseThrow();
        CampanhaVersao versao = campanha.versaoParaRevisao();
        List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
        assertThat(regras.get(0).getCompleta()).isFalse();
    }

    @Test
    @DisplayName("BUG-102: Deve manter trilha de auditoria completa no histórico de pendências")
    void deveConsultarHistoricoCompletoDeAuditoriaDeAceites() throws Exception {
        Long campanhaId = criarCampanhaComPendenciaImpeditiva();

        AceitarPendenciaRequest aceiteReq = new AceitarPendenciaRequest(
                null,
                "pendencia:loja:999",
                "LOJA_FORA_CATALOGO",
                "filtros.codLoja",
                "Mensagem",
                null,
                "gestor.davi@empresa.com",
                "Aceite inicial."
        );

        String aceiteRes = mvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(aceiteReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long aceiteId = json.readTree(aceiteRes).path("id").asLong();

        RevogarAceitePendenciaRequest revogarReq = new RevogarAceitePendenciaRequest(
                "compliance.auditor@empresa.com",
                "Revogado por auditoria."
        );

        mvc.perform(post("/api/v1/campanhas/{id}/pendencias/{aceiteId}/revogar", campanhaId, aceiteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(revogarReq)))
                .andExpect(status().isOk());

        // Consulta histórico de auditoria
        mvc.perform(get("/api/v1/campanhas/{id}/pendencias/historico", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].status", is("REVOGADA")))
                .andExpect(jsonPath("$[0].aceitoPor", is("gestor.davi@empresa.com")))
                .andExpect(jsonPath("$[0].revogadoPor", is("compliance.auditor@empresa.com")))
                .andExpect(jsonPath("$[0].motivoRevogacao", is("Revogado por auditoria.")));
    }

    @Test
    @DisplayName("BUG-102: Deve permitir avanço na esteira somente após aceite formal da pendência impeditiva")
    void devePermitirAvancoNaEsteiraAposAceiteFormalDePendencias() throws Exception {
        Long campanhaId = criarCampanhaComPendenciaImpeditiva();

        // 1. Gera programa Python e aprova na Etapa 2
        String codigoPython = """
            def calcular_comissao(venda):
                return {
                    'venda_id': venda.get('id'),
                    'valor_comissao': 100.0,
                    'taxa_aplicada': 0.035,
                    'regras_aplicadas': ['regra-pendente-1']
                }
            """;

        String respProg = mvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AtualizarProgramaPythonRequest(codigoPython, "analista.davi", "Versão com regra 999"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String checksum = json.readTree(respProg).path("checksumSha256").asText();

        var aprovarResp = mvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AprovarProgramaPythonRequest("revisor.tecnico", checksum, "Homologado tecnicamente"))))
                .andReturn().getResponse();
        assertThat(aprovarResp.getStatus()).isEqualTo(200);

        // 2. Tentar avançar para Etapa 3 (Simulação) deve falhar porque a regra ainda está bloqueada pela pendência
        mvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AvancarEsteiraRequest(3, "analista.davi"))))
                .andExpect(status().isBadRequest());

        // 3. Registra o aceite formal da pendência
        AceitarPendenciaRequest aceiteReq = new AceitarPendenciaRequest(
                null,
                "pendencia:loja:999",
                "LOJA_FORA_CATALOGO",
                "filtros.codLoja",
                "Mensagem",
                null,
                "gestor.davi@empresa.com",
                "Mitigado em comitê de vendas."
        );

        mvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(aceiteReq)))
                .andExpect(status().isCreated());

        // 4. Agora o avanço para Etapa 3 (Simulação) deve ser aprovado com sucesso!
        mvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AvancarEsteiraRequest(3, "analista.davi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual", is(3)))
                .andExpect(jsonPath("$.estadoEsteira", is("EM_SIMULACAO")));
    }
}
