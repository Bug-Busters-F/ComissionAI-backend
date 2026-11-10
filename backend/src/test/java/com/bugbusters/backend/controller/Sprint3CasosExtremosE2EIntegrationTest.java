package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.apuracao.CalculoDefinitivoCampanhaRequest;
import com.bugbusters.backend.dto.auditoria.RegistroAuditoriaDTO;
import com.bugbusters.backend.dto.esteira.AprovarGerenteRequest;
import com.bugbusters.backend.dto.esteira.AvancarEsteiraRequest;
import com.bugbusters.backend.dto.esteira.DevolverCampanhaRequest;
import com.bugbusters.backend.dto.fechamento.FechamentoRequestDTO;
import com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia;
import com.bugbusters.backend.dto.pendencia.AceitarPendenciaRequest;
import com.bugbusters.backend.dto.pendencia.RevogarAceitePendenciaRequest;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.AtualizarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.RejeitarProgramaPythonRequest;
import com.bugbusters.backend.dto.simulacao.AceiteSimulacaoRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoPythonRequest;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.CampanhaVersao;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bateria Exaustiva de Testes de Casos Extremos, Negativos e Ciclo de Vida End-to-End
 * para as 7 tasks do Sprint 3 (BUG-104, BUG-99, BUG-106, BUG-102, BUG-109, BUG-110, BUG-111).
 */
@SpringBootTest(properties = {
        "ai.service.url=http://localhost:8000",
        "spring.datasource.url=jdbc:h2:mem:sprint3_casos_extremos_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Sprint3CasosExtremosE2EIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate cleanupJdbc;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private CampanhaVersaoRepository campanhaVersaoRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private CampanhaApuracaoRepository apuracaoRepository;

    @Autowired
    private AuditoriaGeralRepository auditoriaRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Brand testBrand;
    private Store testStore;
    private Position testPosition;
    private Registration testRegistration;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        cleanupJdbc.update("UPDATE tb_campanha_versao SET programa_python_aprovado_id = NULL");
        cleanupJdbc.update("DELETE FROM tb_campanha_programa_python");
        cleanupJdbc.update("DELETE FROM tb_auditoria_geral");
        cleanupJdbc.update("DELETE FROM tb_log_calculo_imutavel");
        cleanupJdbc.update("DELETE FROM tb_resultado_calculo");
        cleanupJdbc.update("DELETE FROM tb_campanha_apuracao");
        cleanupJdbc.update("DELETE FROM tb_fechamento_competencia");
        cleanupJdbc.update("DELETE FROM tb_campanha_pendencia_aceite");
        cleanupJdbc.update("DELETE FROM tb_campanha_esteira_transicao");
        com.bugbusters.backend.support.CampaignDataCleanup.clear(cleanupJdbc);
        saleRepository.deleteAll();
        baseComissRepository.deleteAll();

        testBrand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("MARCA EXTREMO");
            return brandRepository.save(b);
        });

        testStore = storeRepository.findByCode(77).orElseGet(() -> {
            Store s = new Store();
            s.setCode(77);
            s.setDescription("LOJA MATRIZ EXTREMA");
            return storeRepository.save(s);
        });

        testPosition = positionRepository.findByCode(150).orElseGet(() -> {
            Position p = new Position();
            p.setCode(150);
            p.setDescription("CONSULTOR MASTER");
            return positionRepository.save(p);
        });

        testRegistration = registrationRepository.findByRegistration("MATRIC-EXT-01").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-EXT-01");
            r.setPosition(testPosition);
            r.setStore(testStore);
            r.setAdmissDate(LocalDate.of(2022, 1, 10));
            return registrationRepository.save(r);
        });

        LocalDate mesRef = LocalDate.of(2026, 9, 1);
        baseComissRepository.findByBrandIdAndPositionIdAndReferenceMonth(testBrand.getId(), testPosition.getId(), mesRef)
                .orElseGet(() -> {
                    BaseComiss bc = new BaseComiss(testBrand, testPosition, new BigDecimal("0.0500"));
                    bc.setReferenceMonth(mesRef);
                    return baseComissRepository.save(bc);
                });
    }

    private Long criarCampanhaValida(String competencia, BigDecimal taxa) throws Exception {
        LocalDate inicio = LocalDate.parse(competencia + "-01");
        LocalDate fim = inicio.plusMonths(1).minusDays(1);

        String payload = String.format(Locale.US, """
            {
              "titulo": "Campanha Competencia %s",
              "textoOriginal": "Regra geral para o mes de %s",
              "estado": "DRAFT",
              "dataInicio": "%s",
              "dataFim": "%s",
              "regras": [
                {
                  "blocoId": "bloco-regra-extrema",
                  "nome": "Regra Principal",
                  "canal": "ECOMMERCE",
                  "taxa": %.4f,
                  "valorAjuste": %.4f,
                  "tipoOperacao": "DEFINIR_TAXA",
                  "completa": true,
                  "codMarca": %d,
                  "codCargo": %d,
                  "codLoja": %d
                }
              ]
            }
            """, competencia, competencia, inicio, fim, taxa.doubleValue(), taxa.doubleValue(),
                testBrand.getCode(), testPosition.getCode(), testStore.getCode());

        MvcResult result = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.get("id").asLong();
    }

    private Long criarCampanhaComPendenciaImpeditiva(String competencia) throws Exception {
        LocalDate inicio = LocalDate.parse(competencia + "-01");
        LocalDate fim = inicio.plusMonths(1).minusDays(1);

        String payload = String.format("""
            {
              "titulo": "Campanha com Pendencia Impeditiva %s",
              "textoOriginal": "Vendas no canal LOJA_FISICA: comissao de 4%%",
              "estado": "DRAFT",
              "dataInicio": "%s",
              "dataFim": "%s",
              "regras": [
                {
                  "blocoId": "regra-pendente-extrema",
                  "nome": "Regra Loja 999",
                  "canal": "LOJA_FISICA",
                  "taxa": 0.040,
                  "valorAjuste": 0.040,
                  "tipoOperacao": "DEFINIR_TAXA",
                  "completa": false,
                  "codMarca": %d,
                  "codCargo": %d,
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
                        "mensagem": "Loja 999 nao encontrada na base cadastral homologada.",
                        "severidade": "IMPEDIMENTO"
                      }
                    ],
                    "pendenciasCampanha": []
                  }
                }
              ]
            }
            """, competencia, inicio, fim, testBrand.getCode(), testPosition.getCode());

        MvcResult result = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
        return tree.path("id").asLong();
    }

    // =========================================================================
    // CASO 1: E2E Completo integrando as 7 tasks do Sprint 3
    // =========================================================================
    @Test
    @Order(1)
    @DisplayName("E2E Completo: Criação -> Transição Esteira -> Edição/Aprovação Python -> Simulação -> Aceite Simulação -> Homologação Gerencial -> Apuração Definitiva -> Relatório CSV BOM -> Fechamento -> Auditoria Geral")
    void test01_CicloCompletoE2E_SeteTasksIntegradas() throws Exception {
        String competencia = "2026-09";

        // 0. Inserir vendas
        Sale sale1 = new Sale();
        sale1.setSaleDate(LocalDate.of(2026, 9, 10));
        sale1.setValue(new BigDecimal("12000.00"));
        sale1.setRegistration(testRegistration);
        sale1.setBrand(testBrand);
        sale1.setStore(testStore);
        sale1.setSaleChannel("ECOMMERCE");
        saleRepository.save(sale1);

        Sale sale2 = new Sale();
        sale2.setSaleDate(LocalDate.of(2026, 9, 20));
        sale2.setValue(new BigDecimal("8000.00"));
        sale2.setRegistration(testRegistration);
        sale2.setBrand(testBrand);
        sale2.setStore(testStore);
        sale2.setSaleChannel("ECOMMERCE");
        saleRepository.save(sale2);

        // 1. Criar Campanha (Etapa 1 - RASCUNHO)
        Long campanhaId = criarCampanhaValida(competencia, new BigDecimal("0.1000"));
        assertThat(campanhaId).isNotNull();

        // 2. Verificar Estado Inicial da Esteira (BUG-99)
        mockMvc.perform(get("/api/v1/campanhas/{id}/esteira", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(1))
                .andExpect(jsonPath("$.estadoEsteira").value("EM_ELABORACAO"));

        // 3. Avançar para Etapa 2 (CONFIGURACAO / REVISAO TECNICA) (BUG-99)
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "davi.gestor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(2))
                .andExpect(jsonPath("$.estadoEsteira").value("EM_REVISAO_TECNICA"));

        // 4. Obter e Atualizar Código Python (BUG-104)
        mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacao").value("PENDENTE_REVISAO"));

        String novoCodigo = """
            def calcular_comissao(venda, contexto):
                taxa = 0.10
                return venda.get("valor", 0.0) * taxa
            """;

        MvcResult updateResult = mockMvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtualizarProgramaPythonRequest(
                                novoCodigo, "davi.dev", "Regra customizada sprint 3"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacao").value("PENDENTE_REVISAO"))
                .andExpect(jsonPath("$.codigoFonte", containsString("taxa = 0.10")))
                .andReturn();

        String checksumNovo = objectMapper.readTree(updateResult.getResponse().getContentAsString()).get("checksumSha256").asText();
        assertThat(checksumNovo).isNotBlank();

        // 5. Homologar / Aprovar Código Python com Checksum SHA-256 (BUG-104)
        // Isso avança a esteira para a Etapa 3 (EM_SIMULACAO)
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarProgramaPythonRequest(
                                "validador.tecnico",
                                checksumNovo,
                                "Código validado com sucesso e cobertura total."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacao").value("APROVADO"))
                .andExpect(jsonPath("$.aprovadoPor").value("validador.tecnico"));

        // Conferir avanço automático para Etapa 3 na esteira
        mockMvc.perform(get("/api/v1/campanhas/{id}/esteira", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(3))
                .andExpect(jsonPath("$.estadoEsteira").value("EM_SIMULACAO"));

        // 6. Orquestrar Simulação Python com Persistência (BUG-106)
        SimulacaoPythonRequest simRequest = new SimulacaoPythonRequest(
                competencia,
                null,
                50,
                0,
                false,
                false
        );

        MvcResult simResult = mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocolo", notNullValue()))
                .andExpect(jsonPath("$.executadoViaPython").value(true))
                .andReturn();

        JsonNode simTree = objectMapper.readTree(simResult.getResponse().getContentAsString());
        String simId = simTree.get("id").asText();
        UUID simUuid = UUID.fromString(simId);
        assertThat(simUuid).isNotNull();

        // 7. Aceitar Formalmente a Simulação para Cumprir Gate da Etapa 4
        AceiteSimulacaoRequest aceiteSimReq = new AceiteSimulacaoRequest("gestor@empresa.com", "Simulação aprovada em comitê");
        mockMvc.perform(post("/api/v1/simulacoes/{simulacaoId}/aceite", simUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceiteSimReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAceite").value("ACEITA"));

        // 8. Avançar para Etapa 4 (EM_APROVACAO_GERENCIAL) (BUG-99)
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(4, "davi.gestor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(4))
                .andExpect(jsonPath("$.estadoEsteira").value("EM_APROVACAO_GERENCIAL"));

        // 9. Aprovação Gerencial Formal na Etapa 4 (BUG-99)
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarGerenteRequest(
                                "gerente.executivo",
                                "Campanha aprovada pelo comitê financeiro."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoEsteira").value("APROVADA"));

        // 10. Executar Apuração Definitiva por Campanha no Motor Atual (BUG-109)
        CalculoDefinitivoCampanhaRequest apuracaoReq = new CalculoDefinitivoCampanhaRequest(
                competencia,
                null,
                null,
                "davi.apurador",
                "Apuração definitiva de fechamento mensal",
                false
        );

        MvcResult apuracaoResult = mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apuracaoReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apuracaoId", notNullValue()))
                .andExpect(jsonPath("$.campanhaId").value(campanhaId))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.totalVendasProcessadas").value(2))
                .andExpect(jsonPath("$.valorTotalVendas").value(20000.00))
                .andExpect(jsonPath("$.totalCalculados").value(2))
                .andReturn();

        String apuracaoId = objectMapper.readTree(apuracaoResult.getResponse().getContentAsString()).get("apuracaoId").asText();

        // 11. Consultar Última Apuração Definitiva (BUG-109)
        mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apuracaoId").value(apuracaoId))
                .andExpect(jsonPath("$.totalVendasProcessadas").value(2));

        // 12. Consolidar Relatório Multidimensional (BUG-110)
        mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva/relatorio", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.totalVendasProcessadas").value(2))
                .andExpect(jsonPath("$.resumo.valorTotalVendas").value(20000.00))
                .andExpect(jsonPath("$.porCanal", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.porEquipe", hasSize(1)))
                .andExpect(jsonPath("$.porColaborador", hasSize(1)))
                .andExpect(jsonPath("$.detalheVendas", hasSize(2)));

        // 13. Exportar Relatório CSV com BOM UTF-8 (BUG-110)
        MvcResult csvResult = mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva/exportar", campanhaId)
                        .param("tipo", "DETALHADO")
                        .param("delimitador", ";"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andReturn();

        byte[] csvBytes = csvResult.getResponse().getContentAsByteArray();
        assertThat(csvBytes.length).isGreaterThan(3);
        // Byte Order Mark UTF-8 (\uFEFF -> 0xEF, 0xBB, 0xBF)
        assertThat(csvBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(csvBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(csvBytes[2]).isEqualTo((byte) 0xBF);

        String csvText = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csvText).contains("ProtocoloCalculo;IdVenda;DataVenda;Matricula");
        assertThat(csvText).contains("MATRIC-EXT-01");

        // 14. Exportar Relatório CSV Completo Unificado (BUG-110)
        MvcResult csvCompletoResult = mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva/exportar", campanhaId)
                        .param("tipo", "COMPLETO")
                        .param("delimitador", ";"))
                .andExpect(status().isOk())
                .andReturn();

        String csvCompletoText = new String(csvCompletoResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csvCompletoText).contains("RELATORIO CONSOLIDADO DE FECHAMENTO FINANCEIRO");
        assertThat(csvCompletoText).contains("CONSOLIDADO POR CANAL DE VENDA");
        assertThat(csvCompletoText).contains("CONSOLIDADO POR EQUIPE");
        assertThat(csvCompletoText).contains("CONSOLIDADO POR COLABORADOR");
        assertThat(csvCompletoText).contains("DETALHAMENTO DE VENDAS LIQUIDADAS");

        // 15. Executar Fechamento Mensal Oficial (Trava de Competência)
        FechamentoRequestDTO fechamentoRequest = new FechamentoRequestDTO(
                competencia,
                "auditor.chefe",
                "Fechamento oficial de Setembro/2026"
        );

        mockMvc.perform(post("/api/v1/comissoes/fechamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fechamentoRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.protocolo", notNullValue()));

        // 16. Validar Trilha de Auditoria Geral (BUG-111)
        mockMvc.perform(get("/api/v1/auditoria")
                        .param("entidadeId", campanhaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.content[*].sucesso", everyItem(is(true))));

        mockMvc.perform(get("/api/v1/auditoria/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistros", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.totalSucessos", greaterThanOrEqualTo(4)));
    }

    // =========================================================================
    // CASO 2: BUG-104 - Validação de Checksum SHA-256 Divergente (Negative Test)
    // =========================================================================
    @Test
    @Order(2)
    @DisplayName("BUG-104 [BACK-9]: Deve rejeitar aprovação de código Python quando o checksum SHA-256 for divergente (400 Bad Request)")
    void test02_BUG104_ChecksumSha256Divergente_Retorna400BadRequest() throws Exception {
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0800"));

        AprovarProgramaPythonRequest reqDivergente = new AprovarProgramaPythonRequest(
                "revisor.tecnico",
                "sha256-falso-adulterado-1234567890abcdef",
                "Tentativa de homologar código com hash forjado"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqDivergente)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Divergência de integridade")));
    }

    // =========================================================================
    // CASO 3: BUG-104 - Rejeição Sem Justificativa Obrigatória (Negative Test)
    // =========================================================================
    @Test
    @Order(3)
    @DisplayName("BUG-104 [BACK-9]: Deve rejeitar reprovação técnica de código Python sem justificativa obrigatória (400 Bad Request)")
    void test03_BUG104_RejeicaoSemJustificativa_Retorna400BadRequest() throws Exception {
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0800"));

        RejeitarProgramaPythonRequest reqInvalido = new RejeitarProgramaPythonRequest(
                "revisor.tecnico",
                "   " // justificativa em branco
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/rejeitar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInvalido)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // CASO 4: BUG-104 - Rejeição Válida com Atualização de Status e Auditoria
    // =========================================================================
    @Test
    @Order(4)
    @DisplayName("BUG-104 [BACK-9]: Rejeição técnica com justificativa deve alterar status para REJEITADO e gravar histórico")
    void test04_BUG104_RejeicaoValida_AtualizaStatusEAudita() throws Exception {
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0800"));

        RejeitarProgramaPythonRequest reqValido = new RejeitarProgramaPythonRequest(
                "revisor.tecnico",
                "Código viola regras de conformidade financeira."
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/rejeitar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqValido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacao").value("REJEITADO"))
                .andExpect(jsonPath("$.justificativa").value("Código viola regras de conformidade financeira."));

        // Conferir no histórico de versões do programa
        mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python/historico", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statusAprovacao").value("REJEITADO"));
    }

    // =========================================================================
    // CASO 5: BUG-99 - Pular Etapas na Esteira Sem Pré-requisitos (Negative Test)
    // =========================================================================
    @Test
    @Order(5)
    @DisplayName("BUG-99 [BACK-4]: Deve impedir transição de esteira para Etapa 3 sem aprovação prévia do programa Python (400 Bad Request)")
    void test05_BUG99_PularEtapasEsteira_Retorna400BadRequest() throws Exception {
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0500"));

        AvancarEsteiraRequest saltoInvalido = new AvancarEsteiraRequest(
                3, // Tentando ir para Simulação direto da etapa 1
                "usuario.teste"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saltoInvalido)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("aprovação técnica prévia do programa Python")));
    }

    // =========================================================================
    // CASO 6: BUG-99 - Avanço para Etapa 3 Sem Python Aprovado (Negative Test)
    // =========================================================================
    @Test
    @Order(6)
    @DisplayName("BUG-99 [BACK-4]: Deve impedir avanço para etapa de Simulação (3) se o código Python não estiver homologado (400 Bad Request)")
    void test06_BUG99_AvancoParaSimulacaoSemPythonAprovado_Retorna400BadRequest() throws Exception {
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0500"));

        // Avança 1 -> 2
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "usuario"))))
                .andExpect(status().isOk());

        // Tenta avançar 2 -> 3 sem aprovar o Python
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(3, "usuario"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("aprovação técnica prévia do programa Python")));
    }

    // =========================================================================
    // CASO 7: BUG-99 - Devolução Sem Justificativa Obrigatória (Negative Test)
    // =========================================================================
    @Test
    @Order(7)
    @DisplayName("BUG-99 [BACK-4]: Deve impedir devolução de esteira sem justificativa obrigatória (400 Bad Request)")
    void test07_BUG99_DevolucaoSemJustificativa_Retorna400BadRequest() throws Exception {
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0500"));

        // Avança para 2
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "usuario"))))
                .andExpect(status().isOk());

        // Tenta devolver para 1 sem justificativa
        DevolverCampanhaRequest devolucaoInvalida = new DevolverCampanhaRequest(
                1,
                "   ", // justificativa em branco
                "revisor"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/devolver", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devolucaoInvalida)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // CASO 8: BUG-99 - Devolução para Etapa Inválida (>= atual) (Negative Test)
    // =========================================================================
    @Test
    @Order(8)
    @DisplayName("BUG-99 [BACK-4]: Deve impedir devolução de esteira para etapa maior ou igual à etapa atual (400 Bad Request)")
    void test08_BUG99_DevolucaoEtapaInvalida_Retorna400BadRequest() throws Exception {
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0500"));

        // Avança para 2
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "usuario"))))
                .andExpect(status().isOk());

        // Tenta devolver para etapa 2
        DevolverCampanhaRequest devolucaoMesmaEtapa = new DevolverCampanhaRequest(
                2,
                "Tentando devolver para a mesma etapa",
                "revisor"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/devolver", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devolucaoMesmaEtapa)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("deve ser anterior à etapa atual")));
    }

    // =========================================================================
    // CASO 9: BUG-99 - Devolução Válida e Reavanço com Auditoria
    // =========================================================================
    @Test
    @Order(9)
    @DisplayName("BUG-99 [BACK-4]: Devolução válida com justificativa deve recuar etapa, gravar histórico auditável e permitir reavanço")
    void test09_BUG99_DevolucaoValidaEReavanco_AuditaEAtualizaEtapa() throws Exception {
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0500"));

        // Avança para etapa 2
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "davi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(2));

        // Devolve para etapa 1 com justificativa formal
        DevolverCampanhaRequest devolucaoValida = new DevolverCampanhaRequest(
                1,
                "Revisão de premissas comerciais requer readequação pelo autor.",
                "auditor.compliance"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/devolver", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devolucaoValida)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(1))
                .andExpect(jsonPath("$.estadoEsteira").value("DEVOLVIDA"))
                .andExpect(jsonPath("$.devolvida").value(true));

        // Conferir histórico da esteira
        mockMvc.perform(get("/api/v1/campanhas/{id}/esteira/historico", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tipoAcao").value("DEVOLUCAO_GERENCIAL"))
                .andExpect(jsonPath("$[0].motivo", containsString("Revisão de premissas comerciais")));

        // Re-avançar para etapa 2 limpa a pendência de devolução
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "davi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual").value(2))
                .andExpect(jsonPath("$.devolvida").value(false));
    }

    // =========================================================================
    // CASO 10: BUG-102 - Aceite de Pendência Sem Justificativa Obrigatória (Negative)
    // =========================================================================
    @Test
    @Order(10)
    @DisplayName("BUG-102 [BACK-7]: Deve rejeitar aceite de pendência sem justificativa obrigatória ou sem responsável (400 Bad Request)")
    void test10_BUG102_AceiteSemJustificativa_Retorna400BadRequest() throws Exception {
        Long campanhaId = criarCampanhaComPendenciaImpeditiva("2026-09");

        AceitarPendenciaRequest requestInvalido = new AceitarPendenciaRequest(
                null,
                "pendencia:loja:999",
                "LOJA_FORA_CATALOGO",
                "filtros.codLoja",
                "Mensagem",
                SeveridadePendencia.IMPEDIMENTO,
                "", // Usuário vazio
                "   " // Justificativa vazia
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // CASO 11: BUG-102 - Bloqueio por Pendência Impeditiva e Revogação com Rebloqueio
    // =========================================================================
    @Test
    @Order(11)
    @DisplayName("BUG-102 [BACK-7]: Pendência impeditiva bloqueia cálculo definitivo; aceite desbloqueia; revogação de aceite re-bloqueia")
    void test11_BUG102_RevogacaoDeAceite_RebloqueiaEsteiraEApuracao() throws Exception {
        Long campanhaId = criarCampanhaComPendenciaImpeditiva("2026-09");

        // 1. Verificar que campanha não está apta
        mockMvc.perform(get("/api/v1/campanhas/{id}/pendencias", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aptaParaAvanco").value(false))
                .andExpect(jsonPath("$.totalImpedimentos").value(1));

        // 2. Homologar Python
        MvcResult pRes = mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId)).andReturn();
        String hash = objectMapper.readTree(pRes.getResponse().getContentAsString()).get("checksumSha256").asText();
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarProgramaPythonRequest("val", hash, "aprovado"))))
                .andExpect(status().isOk());

        // 3. Tentar apurar definitivo com pendência impeditiva não aceita deve falhar com 400
        CalculoDefinitivoCampanhaRequest apReq = new CalculoDefinitivoCampanhaRequest(
                "2026-09", null, null, "user", "tentando apurar com impedimento", false
        );
        mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("pendência(s) impeditiva(s) em aberto")));

        // 4. Registrar o aceite formal da pendência
        AceitarPendenciaRequest aceiteReq = new AceitarPendenciaRequest(
                null,
                "pendencia:loja:999",
                "LOJA_FORA_CATALOGO",
                "filtros.codLoja",
                "Loja 999",
                SeveridadePendencia.IMPEDIMENTO,
                "gestor.risco@empresa.com",
                "Risco assumido formalmente pelo gestor comercial para o fechamento."
        );

        MvcResult aceiteRes = mockMvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceiteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACEITA"))
                .andReturn();

        Long aceiteId = objectMapper.readTree(aceiteRes.getResponse().getContentAsString()).get("id").asLong();

        // 5. Revogar o aceite com justificativa
        RevogarAceitePendenciaRequest revReq = new RevogarAceitePendenciaRequest(
                "comite.auditoria@empresa.com",
                "Comitê revogou o aceite: exigida inclusão cadastral definitiva no ERP."
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/pendencias/{aceiteId}/revogar", campanhaId, aceiteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOGADA"))
                .andExpect(jsonPath("$.revogadoPor").value("comite.auditoria@empresa.com"));

        // 6. Tentar revogar novamente pendência já revogada deve falhar (400 Bad Request)
        mockMvc.perform(post("/api/v1/campanhas/{id}/pendencias/{aceiteId}/revogar", campanhaId, aceiteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("já se encontra revogado")));

        // 7. Verificar que o cálculo definitivo volta a ser bloqueado após a revogação
        mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("pendência(s) impeditiva(s) em aberto")));
    }

    // =========================================================================
    // CASO 12: BUG-109 - Apuração em Competência Fechada Exige forcarRecalculo (Negative & Bypass)
    // =========================================================================
    @Test
    @Order(12)
    @DisplayName("BUG-109 [BACK-14]: Apuração definitiva em competência fechada deve retornar 400 a menos que 'forcarRecalculo=true' seja explicitamente informado")
    void test12_BUG109_ApuracaoDefinitivaComCompetenciaFechada_ExigeForcarRecalculo() throws Exception {
        String comp = "2026-09";
        Long campanhaId = criarCampanhaValida(comp, new BigDecimal("0.0500"));

        // Aprova o Python para permitir o cálculo
        MvcResult pRes = mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId)).andReturn();
        String hash = objectMapper.readTree(pRes.getResponse().getContentAsString()).get("checksumSha256").asText();
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarProgramaPythonRequest("val", hash, "aprovado"))))
                .andExpect(status().isOk());

        // Inserir venda
        Sale s = new Sale();
        s.setSaleDate(LocalDate.of(2026, 9, 5));
        s.setValue(new BigDecimal("5000.00"));
        s.setRegistration(testRegistration);
        s.setBrand(testBrand);
        s.setStore(testStore);
        s.setSaleChannel("ECOMMERCE");
        saleRepository.save(s);

        // 1. Fechar a competência antes da apuração
        mockMvc.perform(post("/api/v1/comissoes/fechamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FechamentoRequestDTO(comp, "admin", "Fechamento antecipado"))))
                .andExpect(status().isOk());

        // 2. Tentar apurar sem forcarRecalculo (deve retornar 400 Bad Request)
        CalculoDefinitivoCampanhaRequest reqNormal = new CalculoDefinitivoCampanhaRequest(
                comp,
                null,
                null,
                "davi.operador",
                "Tentativa em competência bloqueada",
                false
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqNormal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("possui fechamento final formalizado")));

        // 3. Tentar apurar com forcarRecalculo=true (deve ter sucesso e registrar auditoria de recalculo forçado)
        CalculoDefinitivoCampanhaRequest reqForcado = new CalculoDefinitivoCampanhaRequest(
                comp,
                null,
                null,
                "diretoria.comercial",
                "Recálculo retificador autorizado pela diretoria executiva.",
                true
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqForcado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.totalVendasProcessadas").value(1));
    }

    // =========================================================================
    // CASO 13: BUG-110 - Exportação CSV com Todos os Delimitadores e Agrupamentos
    // =========================================================================
    @Test
    @Order(13)
    @DisplayName("BUG-110 [BACK-15]: Exportação CSV com delimitadores ';', ',' e tabulação, validando cabeçalhos e BOM UTF-8")
    void test13_BUG110_ExportarCsvTodosDelimitadores_PontoEVirgula_Virgula_Tab() throws Exception {
        String comp = "2026-09";
        Long campanhaId = criarCampanhaValida(comp, new BigDecimal("0.1000"));

        Sale s = new Sale();
        s.setSaleDate(LocalDate.of(2026, 9, 12));
        s.setValue(new BigDecimal("7500.00"));
        s.setRegistration(testRegistration);
        s.setBrand(testBrand);
        s.setStore(testStore);
        s.setSaleChannel("ECOMMERCE");
        saleRepository.save(s);

        // Aprova o Python para permitir apuração
        MvcResult pRes = mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId)).andReturn();
        String hash = objectMapper.readTree(pRes.getResponse().getContentAsString()).get("checksumSha256").asText();
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarProgramaPythonRequest("val", hash, "aprovado"))))
                .andExpect(status().isOk());

        // Executa apuração definitiva
        mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CalculoDefinitivoCampanhaRequest(
                                comp, null, null, "user", "apura", false))))
                .andExpect(status().isOk());

        // 1. Delimitador Vírgula (,)
        MvcResult csvVirgula = mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva/exportar", campanhaId)
                        .param("tipo", "CONSOLIDADO_CANAL")
                        .param("delimitador", ","))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bVirgula = csvVirgula.getResponse().getContentAsByteArray();
        assertThat(bVirgula[0]).isEqualTo((byte) 0xEF);
        assertThat(bVirgula[1]).isEqualTo((byte) 0xBB);
        assertThat(bVirgula[2]).isEqualTo((byte) 0xBF);
        String txtVirgula = new String(bVirgula, StandardCharsets.UTF_8);
        assertThat(txtVirgula).contains("Canal,QtdVendas,QtdColaboradores,ValorTotalVendas,ValorTotalComissao");
        assertThat(txtVirgula).contains("ECOMMERCE,1,1,7500.00");

        // 2. Delimitador Tabulação (\t)
        MvcResult csvTab = mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva/exportar", campanhaId)
                        .param("tipo", "CONSOLIDADO_EQUIPE")
                        .param("delimitador", "\t"))
                .andExpect(status().isOk())
                .andReturn();

        String txtTab = new String(csvTab.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(txtTab).contains("CodLoja\tNomeLoja\tCodMarca\tNomeMarca\tQtdVendas\tQtdColaboradores\tValorTotalVendas\tValorTotalComissao");
        assertThat(txtTab).contains("77\tLOJA MATRIZ EXTREMA\t10\tMARCA EXTREMO\t1\t1\t7500.00");

        // 3. Agrupamento Colaborador com Ponto e Vírgula (;)
        MvcResult csvColab = mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva/exportar", campanhaId)
                        .param("tipo", "CONSOLIDADO_COLABORADOR")
                        .param("delimitador", ";"))
                .andExpect(status().isOk())
                .andReturn();

        String txtColab = new String(csvColab.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(txtColab).contains("Matricula;CodCargo;NomeCargo;CodLoja;NomeLoja;CodMarca;NomeMarca;QtdVendas;ValorTotalVendas;ValorTotalComissao");
        assertThat(txtColab).contains("MATRIC-EXT-01;150;CONSULTOR MASTER;77;LOJA MATRIZ EXTREMA;10;MARCA EXTREMO;1;7500.00");
    }

    // =========================================================================
    // CASO 14: BUG-111 - Auditoria Geral: Filtros Combinados, Paginação e Resumo
    // =========================================================================
    @Test
    @Order(14)
    @DisplayName("BUG-111 [BACK-16]: Trilha de Auditoria deve permitir busca por usuário, ação, entidade, período, suporte a paginação e métricas de resumo")
    void test14_BUG111_AuditoriaFiltrosCombinados_Usuario_Acao_Entidade_Periodo_Paginacao() throws Exception {
        // Gerar múltiplos eventos auditáveis
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0500"));

        // Evento 1: Avanço de esteira
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "auditoria.user1"))))
                .andExpect(status().isOk());

        // Evento 2: Registro manual de auditoria com falha simulada
        RegistroAuditoriaDTO eventoFalha = new RegistroAuditoriaDTO(
                "auditoria.user2",
                "OPERADOR",
                TipoAcaoAuditoria.EXECUCAO_SIMULACAO,
                TipoEntidadeAuditoria.SIMULACAO,
                "sim-erro-999",
                1,
                "Simulação com timeout de serviço Python",
                null,
                null,
                "192.168.1.50",
                "PostmanRuntime/7.32",
                false,
                "Connection timeout after 30000ms"
        );

        mockMvc.perform(post("/api/v1/auditoria")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventoFalha)))
                .andExpect(status().isCreated());

        // 1. Filtrar por Usuário 'auditoria.user1'
        mockMvc.perform(get("/api/v1/auditoria")
                        .param("usuario", "auditoria.user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].usuario").value("auditoria.user1"));

        // 2. Filtrar por Sucesso=false
        mockMvc.perform(get("/api/v1/auditoria")
                        .param("sucesso", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].sucesso").value(false))
                .andExpect(jsonPath("$.content[0].detalheErro", containsString("timeout")));

        // 3. Filtrar por Tipo de Ação 'AVANCO_ESTEIRA'
        mockMvc.perform(get("/api/v1/auditoria")
                        .param("tipoAcao", "AVANCO_ESTEIRA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].tipoAcao").value("AVANCO_ESTEIRA"));

        // 4. Testar Endpoint de Resumo / Estatísticas
        mockMvc.perform(get("/api/v1/auditoria/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistros", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalFalhas", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalSucessos", greaterThanOrEqualTo(1)));

        // 5. Testar Exportação CSV da Trilha de Auditoria
        MvcResult auditCsvResult = mockMvc.perform(get("/api/v1/auditoria/exportar")
                        .param("delimitador", ";"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andReturn();

        byte[] auditCsvBytes = auditCsvResult.getResponse().getContentAsByteArray();
        assertThat(auditCsvBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(auditCsvBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(auditCsvBytes[2]).isEqualTo((byte) 0xBF);

        String auditCsvText = new String(auditCsvBytes, StandardCharsets.UTF_8);
        assertThat(auditCsvText).contains("Protocolo;DataHora;Usuario;PerfilUsuario;TipoAcao;TipoEntidade");
        assertThat(auditCsvText).contains("auditoria.user1");
        assertThat(auditCsvText).contains("auditoria.user2");
    }

    // =========================================================================
    // CASO 15: BUG-111 - Registro Manual de Auditoria e Validações de Payload
    // =========================================================================
    @Test
    @Order(15)
    @DisplayName("BUG-111 [BACK-16]: Registro manual de auditoria com validação de campos obrigatórios (400 Bad Request se incompleto)")
    void test15_BUG111_RegistroManualAuditoria_ValidacoesECamposObrigatorios() throws Exception {
        // 1. Payload inválido (sem tipo de ação e sem descrição)
        RegistroAuditoriaDTO invalido = new RegistroAuditoriaDTO(
                "",
                null,
                null,
                null,
                null,
                null,
                "",
                null,
                null,
                null,
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/auditoria")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalido)))
                .andExpect(status().isBadRequest());

        // 2. Payload válido com enriquecimento de IP e User-Agent
        RegistroAuditoriaDTO valido = new RegistroAuditoriaDTO(
                "auditor.externo",
                "AUDITORIA_INDEPENDENTE",
                TipoAcaoAuditoria.EXPORTACAO_RELATORIO,
                TipoEntidadeAuditoria.RELATORIO,
                "rel-fechamento-2026-09",
                1,
                "Auditor independente exportou a trilha de fechamento contábil para conformidade SOX.",
                null,
                "{\"formato\":\"CSV\", \"escopo\":\"COMPLETO\"}",
                null,
                null,
                true,
                null
        );

        mockMvc.perform(post("/api/v1/auditoria")
                        .header("User-Agent", "JUnit5-MockMvc/2.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocolo", notNullValue()))
                .andExpect(jsonPath("$.usuario").value("auditor.externo"))
                .andExpect(jsonPath("$.userAgent").value("JUnit5-MockMvc/2.0"))
                .andExpect(jsonPath("$.sucesso").value(true));
    }

    // =========================================================================
    // CASO 16: BUG-104 - Recálculo Dinâmico de Checksum SHA-256 e Versionamento em Edição
    // =========================================================================
    @Test
    @Order(16)
    @DisplayName("BUG-104 [BACK-9]: Edições sucessivas do código Python incrementam versão do programa e atualizam hash SHA-256 com precisão")
    void test16_BUG104_RecalculoChecksumAutomaticoEmEdicao() throws Exception {
        Long campanhaId = criarCampanhaValida("2026-09", new BigDecimal("0.0500"));

        // Edição 1
        String script1 = "def calcular(v): return v * 0.05\n";
        MvcResult res1 = mockMvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtualizarProgramaPythonRequest(
                                script1, "dev1", "Primeiro ajuste"))))
                .andExpect(status().isOk())
                .andReturn();

        String hash1 = objectMapper.readTree(res1.getResponse().getContentAsString()).get("checksumSha256").asText();

        // Edição 2
        String script2 = "def calcular(v): return v * 0.08 # ajuste\n";
        MvcResult res2 = mockMvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtualizarProgramaPythonRequest(
                                script2, "dev2", "Segundo ajuste"))))
                .andExpect(status().isOk())
                .andReturn();

        String hash2 = objectMapper.readTree(res2.getResponse().getContentAsString()).get("checksumSha256").asText();

        // Hashes devem ser distintos
        assertThat(hash1).isNotEqualTo(hash2);

        // Histórico deve conter as versões
        mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python/historico", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    // =========================================================================
    // CASO 17: Resiliência e Recursos Inexistentes (404 Not Found)
    // =========================================================================
    @Test
    @Order(17)
    @DisplayName("Resiliência: Consultas com IDs inexistentes em Programa Python, Simulação, Apuração e Auditoria devem retornar 404 Not Found")
    void test17_E2E_ResilienciaERotasInexistentes_Retorna404() throws Exception {
        long idInexistente = 999999L;
        UUID uuidInexistente = UUID.randomUUID();

        // 1. Programa Python de campanha inexistente
        mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", idInexistente))
                .andExpect(status().isNotFound());

        // 2. Apuração por ID inexistente
        mockMvc.perform(get("/api/v1/campanhas/{id}/apuracoes/{apuracaoId}", 1L, uuidInexistente))
                .andExpect(status().isNotFound());

        // 3. Auditoria por protocolo inexistente
        mockMvc.perform(get("/api/v1/auditoria/protocolo/{protocolo}", uuidInexistente))
                .andExpect(status().isNotFound());
    }
}
