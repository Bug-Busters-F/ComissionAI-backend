package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.esteira.AvancarEsteiraRequest;
import com.bugbusters.backend.dto.esteira.DevolverCampanhaRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.EstadoEsteiraCampanha;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampanhaEsteiraIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private Long criarCampanhaTeste(String titulo) throws Exception {
        RegraItemRequest regra1 = new RegraItemRequest(
                "bloco-1",
                "Comissão de 5% no canal ecommerce",
                "Regra E-commerce 5%",
                "ECOMMERCE",
                10, "PRETO", null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA,
                null, null, null, null,
                BigDecimal.valueOf(0.05),
                "5% sobre vendas",
                "taxa = 0.05",
                null,
                true,
                null,
                null,
                null
        );

        CampanhaRequest request = new CampanhaRequest(
                titulo,
                "Texto base da campanha de teste de governança",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                EstadoCampanha.DRAFT,
                List.of(regra1),
                null
        );

        MvcResult res = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    @Test
    @DisplayName("Deve obter o estado inicial da esteira com sucesso")
    void deveObterEstadoInicialDaEsteiraComSucesso() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Estado Inicial");

        mockMvc.perform(get("/api/v1/campanhas/{id}/esteira", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campanhaId").value(campanhaId))
                .andExpect(jsonPath("$.etapaAtual").value(1))
                .andExpect(jsonPath("$.estadoEsteira").value("EM_ELABORACAO"))
                .andExpect(jsonPath("$.devolvida").value(false))
                .andExpect(jsonPath("$.programaPythonAprovado").value(false))
                .andExpect(jsonPath("$.simulacaoAceita").value(false));
    }

    @Test
    @DisplayName("Deve avançar etapa na esteira da proposta (1 -> 2) com sucesso")
    void deveAvancarEtapaNaEsteiraComSucesso() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Avançar Etapa 2");

        AvancarEsteiraRequest req = new AvancarEsteiraRequest(2, "analista.vendas@empresa.com");

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(2))
                .andExpect(jsonPath("$.estadoEsteira").value("EM_REVISAO_TECNICA"));
    }

    private String obterChecksum(Long campanhaId) throws Exception {
        MvcResult progRes = mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(progRes.getResponse().getContentAsString()).get("checksumSha256").asText();
    }

    @Test
    @DisplayName("Deve bloquear salto de etapa (1 -> 3) e avanço para etapa 3 sem aprovação técnica do Python")
    void deveBloquearAvancoParaEtapa3SemAprovacaoDoProgramaPython() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Bloqueio Etapa 3");

        AvancarEsteiraRequest saltoReq = new AvancarEsteiraRequest(3, "analista@empresa.com");
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saltoReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("sequencialmente")));

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "analista@empresa.com"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saltoReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("aprovação técnica prévia do programa Python")));
    }

    @Test
    @DisplayName("Deve transitar automaticamente para etapa 3 e estado EM_SIMULACAO ao aprovar programa Python")
    void deveTransitarParaEtapa3AposAprovarProgramaPython() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Aprovar Python");
        String checksum = obterChecksum(campanhaId);

        AprovarProgramaPythonRequest aprovarReq = new AprovarProgramaPythonRequest(
                "auditor.tecnico@empresa.com",
                checksum,
                "Código homologado com sucesso"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aprovarReq)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/campanhas/{id}/esteira", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(3))
                .andExpect(jsonPath("$.estadoEsteira").value("EM_SIMULACAO"))
                .andExpect(jsonPath("$.programaPythonAprovado").value(true));
    }

    @Test
    @DisplayName("FRONT-9: POST /api/v1/campanhas/{id}/devolver deve devolver para etapa 1 após encaminhamento e invalidar aprovações")
    void deveDevolverParaEtapa1MesmoAposEncaminhamentoEDevolverCampanhaResponse() throws Exception {
        String createBody = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titulo": "Campanha Devolucao FRONT-9 Etapa 1",
                                  "textoOriginal": "Comissão de 5% no canal ecommerce",
                                  "dataInicio": "2026-06-01",
                                  "dataFim": "2026-06-30",
                                  "estado": "DRAFT",
                                  "orcamento": 100000.00,
                                  "orcamentoConfirmado": true,
                                  "regras": [
                                    {
                                      "identificadorLogico": "bloco-1",
                                      "nome": "Regra E-commerce 5%",
                                      "canal": "ECOMMERCE",
                                      "taxa": 0.05,
                                      "completa": true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long campanhaId = objectMapper.readTree(createBody).get("id").asLong();

        // Simular, aceitar e aprovar código antes de encaminhar ao gerente
        String simJson = mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"competencia\":\"2026-06\",\"campanhaId\":%d}", campanhaId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String simId = objectMapper.readTree(simJson).get("id").asText();

        mockMvc.perform(post("/api/v1/simulacoes/{simId}/aceite", simId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceitoPor\":\"negocios@empresa.com\",\"motivo\":\"Aceito para aprovação gerencial\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/campanhas/{id}/aprovacao-codigo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aprovado\":true,\"motivo\":\"Código conferido\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/campanhas/{id}/encaminhar-gerente", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encaminhadaGerente").value(true));

        // Devolve usando exatamente o payload esperado pelo frontend: { motivo, paraEtapa: 1 }
        mockMvc.perform(post("/api/v1/campanhas/{id}/devolver", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Ajustar escopo e faixa inicial\",\"paraEtapa\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campanhaId))
                .andExpect(jsonPath("$.estado").value("DRAFT"))
                .andExpect(jsonPath("$.devolvida").value(true))
                .andExpect(jsonPath("$.estadoEsteira").value("DEVOLVIDA"))
                .andExpect(jsonPath("$.motivoDevolucao").value("Ajustar escopo e faixa inicial"))
                .andExpect(jsonPath("$.etapaDevolucao").value(1))
                .andExpect(jsonPath("$.ultimaEtapa").value(1))
                .andExpect(jsonPath("$.encaminhadaGerente").value(false))
                .andExpect(jsonPath("$.simulacaoAceitaId").value(nullValue()));

        // GET /api/v1/campanhas/{id} expõe metadados completos de devolução e invalida aprovações
        mockMvc.perform(get("/api/v1/campanhas/{id}", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DRAFT"))
                .andExpect(jsonPath("$.devolvida").value(true))
                .andExpect(jsonPath("$.estadoEsteira").value("DEVOLVIDA"))
                .andExpect(jsonPath("$.motivoDevolucao").value("Ajustar escopo e faixa inicial"))
                .andExpect(jsonPath("$.etapaDevolucao").value(1))
                .andExpect(jsonPath("$.devolvidoPor").isNotEmpty())
                .andExpect(jsonPath("$.devolvidoEm").isNotEmpty())
                .andExpect(jsonPath("$.encaminhadaGerente").value(false));

        // Consulta de aprovação de código reflete invalidação após devolução para etapa <= 2
        mockMvc.perform(get("/api/v1/campanhas/{id}/aprovacao-codigo", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacaoCodigo").value("INVALIDADO"));

        // Ativação permanece bloqueada até nova simulação e aprovação de código
        mockMvc.perform(post("/api/v1/campanhas/{id}/ativar", campanhaId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve permitir devolução da proposta pelo gerente com justificativa obrigatória (FRONT-9 / BACK-4)")
    void devePermitirDevolucaoPeloGerenteComJustificativa() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Devolução Gerente");
        String checksum = obterChecksum(campanhaId);

        // Aprova o programa Python para colocar a campanha na etapa 3
        AprovarProgramaPythonRequest aprovarReq = new AprovarProgramaPythonRequest(
                "auditor.tecnico@empresa.com", checksum, "Aprovado"
        );
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aprovarReq)))
                .andExpect(status().isOk());

        // Gerente devolve da etapa 3 para a etapa 1
        DevolverCampanhaRequest devReq = new DevolverCampanhaRequest(
                1,
                "A vigência da campanha deve ser estendida até o fim do trimestre.",
                "gerente.comercial@empresa.com"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/devolver", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoEsteira").value("DEVOLVIDA"))
                .andExpect(jsonPath("$.devolvida").value(true))
                .andExpect(jsonPath("$.etapaAtual").value(1))
                .andExpect(jsonPath("$.devolvidoPor").value("gerente.comercial@empresa.com"))
                .andExpect(jsonPath("$.motivoDevolucao").value("A vigência da campanha deve ser estendida até o fim do trimestre."))
                .andExpect(jsonPath("$.etapaDevolucao").value(1));

        // Verifica que o GET principal de campanhas também expõe o banner de devolução
        mockMvc.perform(get("/api/v1/campanhas/{id}", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoEsteira").value("DEVOLVIDA"))
                .andExpect(jsonPath("$.devolvida").value(true))
                .andExpect(jsonPath("$.devolvidoPor").value("gerente.comercial@empresa.com"))
                .andExpect(jsonPath("$.motivoDevolucao").value("A vigência da campanha deve ser estendida até o fim do trimestre."));
    }

    @Test
    @DisplayName("Deve rejeitar devolução para etapa inválida (maior ou igual à atual)")
    void deveRejeitarDevolucaoParaEtapaInvalida() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Devolução Inválida");

        // Está na etapa 1, não pode devolver para a etapa 1 nem para etapa 2
        DevolverCampanhaRequest devReq = new DevolverCampanhaRequest(
                1,
                "Devolvendo mesmo na etapa 1",
                "gerente@empresa.com"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/devolver", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("deve ser anterior à etapa atual")));
    }

    @Test
    @DisplayName("Deve limpar o estado de devolução quando a esteira reavançar após correções")
    void deveLimparEstadoDeDevolucaoAoReavancar() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Limpar Devolução");
        String checksum = obterChecksum(campanhaId);

        // Aprova o programa Python para ir para etapa 3
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarProgramaPythonRequest("auditor@empresa.com", checksum, "Ok"))))
                .andExpect(status().isOk());

        // Devolve para etapa 2
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/devolver", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DevolverCampanhaRequest(2, "Ajustar código", "gerente@empresa.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devolvida").value(true));

        // Reavalia e aprova novamente o código Python para reavançar para 3
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarProgramaPythonRequest("auditor@empresa.com", checksum, "Código corrigido"))))
                .andExpect(status().isOk());

        // Verifica que o estado de devolução foi limpo
        mockMvc.perform(get("/api/v1/campanhas/{id}/esteira", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devolvida").value(false))
                .andExpect(jsonPath("$.estadoEsteira").value("EM_SIMULACAO"));
    }

    @Test
    @DisplayName("Deve registrar e listar histórico completo de transições na esteira")
    void deveRegistrarEListarHistoricoTransicoes() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Histórico Esteira");

        // Avanço 1 -> 2
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "analista@empresa.com"))))
                .andExpect(status().isOk());

        // Aprovação técnica (2 -> 3)
        String checksum = obterChecksum(campanhaId);
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarProgramaPythonRequest("auditor@empresa.com", checksum, "Ok"))))
                .andExpect(status().isOk());

        // Devolução gerente (3 -> 1)
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/devolver", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DevolverCampanhaRequest(1, "Voltar para proposta", "gerente@empresa.com"))))
                .andExpect(status().isOk());

        // Consulta histórico
        MvcResult res = mockMvc.perform(get("/api/v1/campanhas/{id}/esteira/historico", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andReturn();

        JsonNode items = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(items.get(0).get("tipoAcao").asText()).isEqualTo("DEVOLUCAO_GERENCIAL");
        assertThat(items.get(0).get("responsavel").asText()).isEqualTo("gerente@empresa.com");
        assertThat(items.get(0).get("motivo").asText()).isEqualTo("Voltar para proposta");
    }
}

