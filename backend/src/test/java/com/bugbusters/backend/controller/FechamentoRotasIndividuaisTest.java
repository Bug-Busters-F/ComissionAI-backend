package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.calculo.CalculoCompetenciaRequest;
import com.bugbusters.backend.dto.campanha.AlterarEstadoCampanhaRequest;
import com.bugbusters.backend.dto.campanha.AtivarCampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.fechamento.FechamentoRequestDTO;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bateria exaustiva de testes de rotas individuais para a funcionalidade BACK-11:
 * Fechamento Final de Competência, Apuração Retroativa da Versão Vigente e Bloqueios de Mutações.
 */
@SpringBootTest(properties = {"ai.service.url=http://localhost:8000", "spring.datasource.url=jdbc:h2:mem:fechamento_rotas;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@ActiveProfiles("test")
class FechamentoRotasIndividuaisTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FechamentoCompetenciaRepository fechamentoCompetenciaRepository;

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
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private LogCalculoRepository logCalculoRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Brand brand;
    private Position position;
    private Store store;
    private Registration registration;

    @Autowired private org.springframework.jdbc.core.JdbcTemplate cleanupJdbc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        cleanupJdbc.update("DELETE FROM tb_log_calculo_imutavel");
        resultadoCalculoRepository.deleteAll();
        fechamentoCompetenciaRepository.deleteAll();
        com.bugbusters.backend.support.CampaignDataCleanup.clear(cleanupJdbc);
        saleRepository.deleteAll();
        registrationRepository.deleteAll();
        baseComissRepository.deleteAll();
        storeRepository.deleteAll();
        positionRepository.deleteAll();
        brandRepository.deleteAll();

        brand = new Brand();
        brand.setCode(10);
        brand.setDescription("PRETO");
        brand = brandRepository.save(brand);

        position = new Position();
        position.setCode(100);
        position.setDescription("VENDEDOR");
        position = positionRepository.save(position);

        store = new Store();
        store.setCode(75);
        store.setDescription("LOJA-75");
        store = storeRepository.save(store);

        registration = new Registration();
        registration.setRegistration("MAT-1001");
        registration.setPosition(position);
        registration.setStore(store);
        registration.setAdmissDate(LocalDate.of(2025, 1, 1));
        registration = registrationRepository.save(registration);

        BaseComiss baseComiss = new BaseComiss(brand, position, new BigDecimal("0.0500"));
        baseComiss.setReferenceMonth(LocalDate.of(2026, 10, 1));
        baseComissRepository.save(baseComiss);
    }

    private RegraItemRequest criarItemRegraPadrao(BigDecimal taxa) {
        return new RegraItemRequest(
                "b1", "Regra geral", "Regra Padrao", null,
                brand.getCode(), brand.getDescription(), store.getCode(), position.getCode(), position.getDescription(),
                null, null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, taxa, null, null, null,
                taxa, "Desc", "def calc(): ...", List.of(), true, StatusRegra.DRAFT
        );
    }

    @Nested
    @DisplayName("1. Rota POST /api/v1/comissoes/fechamento (Executar Fechamento)")
    class ExecutarFechamentoTests {

        @Test
        @DisplayName("1.1 - Deve executar fechamento com sucesso consolidando versão vigente v2 para todo o mês")
        void postFechamento_sucessoComVendasEVersaoVigente_retorna200Ok() throws Exception {
            LocalDate inicio = LocalDate.of(2026, 10, 1);
            LocalDate fim = LocalDate.of(2026, 10, 31);

            // Venda 1: início do mês
            Sale sale1 = new Sale();
            sale1.setSaleDate(LocalDate.of(2026, 10, 5));
            sale1.setValue(new BigDecimal("1000.00"));
            sale1.setRegistration(registration);
            sale1.setBrand(brand);
            sale1.setStore(store);
            saleRepository.save(sale1);

            // Venda 2: fim do mês
            Sale sale2 = new Sale();
            sale2.setSaleDate(LocalDate.of(2026, 10, 20));
            sale2.setValue(new BigDecimal("3000.00"));
            sale2.setRegistration(registration);
            sale2.setBrand(brand);
            sale2.setStore(store);
            saleRepository.save(sale2);

            // Campanha com versão vigente número 2 cobrindo todo o mês com taxa 10%
            Campanha campanha = new Campanha("Campanha Outubro", "Comissão 10%", inicio, fim);
            campanha.setEstado(EstadoCampanha.ATIVA);
            campanha = campanhaRepository.save(campanha);

            CampanhaVersao v2 = new CampanhaVersao();
            v2.setCampanha(campanha);
            v2.setNumero(2);
            v2.setStatus(StatusVersaoCampanha.PUBLICADA);
            v2.setTitulo("Campanha Outubro v2");
            v2.setTextoOriginal("Comissão 10%");
            v2.setDataInicio(inicio);
            v2.setDataFim(fim);
            v2.setPublicadoEm(OffsetDateTime.now());
            v2 = campanhaVersaoRepository.save(v2);

            campanha.setVersaoVigente(v2);
            campanhaRepository.save(campanha);

            Regra r2 = new Regra();
            r2.setCampanha(campanha);
            r2.setCampanhaVersao(v2);
            r2.setNome("Regra v2");
            r2.setTaxa(new BigDecimal("0.1000"));
            r2.setStatus(StatusRegra.ATIVA);
            r2.setCompleta(true);
            r2.setDataInicio(inicio);
            r2.setDataFim(fim);
            r2.setCodMarca(brand.getCode());
            r2.setCodCargo(position.getCode());
            regraRepository.save(r2);

            FechamentoRequestDTO req = new FechamentoRequestDTO("2026-10", "auditor@empresa.com", "Fechamento mensal oficial");

            mockMvc.perform(post("/api/v1/comissoes/fechamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.competencia").value("2026-10"))
                    .andExpect(jsonPath("$.totalVendasProcessadas").value(2))
                    .andExpect(jsonPath("$.totalCalculados").value(2))
                    .andExpect(jsonPath("$.totalImpedimentos").value(0))
                    .andExpect(jsonPath("$.valorTotalVendas").value(4000.00))
                    .andExpect(jsonPath("$.valorTotalComissao").value(400.00))
                    .andExpect(jsonPath("$.executadoPor").value("auditor@empresa.com"))
                    .andExpect(jsonPath("$.protocolo").isNotEmpty())
                    .andExpect(jsonPath("$.resultados", hasSize(2)))
                    .andExpect(jsonPath("$.resultados[0].versaoCampanhaNumero").value(2))
                    .andExpect(jsonPath("$.resultados[1].versaoCampanhaNumero").value(2));
        }

        @Test
        @DisplayName("1.2 - Deve executar fechamento com sucesso em competência sem vendas (totais zerados)")
        void postFechamento_competenciaSemVendas_retorna200OkComTotaisZerados() throws Exception {
            FechamentoRequestDTO req = new FechamentoRequestDTO("2026-10", "auditor@empresa.com", "Mês sem movimento");

            mockMvc.perform(post("/api/v1/comissoes/fechamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.competencia").value("2026-10"))
                    .andExpect(jsonPath("$.totalVendasProcessadas").value(0))
                    .andExpect(jsonPath("$.totalCalculados").value(0))
                    .andExpect(jsonPath("$.totalImpedimentos").value(0))
                    .andExpect(jsonPath("$.valorTotalVendas").value(0.00))
                    .andExpect(jsonPath("$.valorTotalComissao").value(0.00));
        }

        @Test
        @DisplayName("1.3 - Deve rejeitar fechamento com competência nula ou em branco (400)")
        void postFechamento_competenciaNulaOuVazia_retorna400BadRequest() throws Exception {
            String payload = """
                    {
                        "competencia": "",
                        "executadoPor": "auditor"
                    }
                    """;

            mockMvc.perform(post("/api/v1/comissoes/fechamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validacoes").isArray());
        }

        @Test
        @DisplayName("1.4 - Deve rejeitar fechamento com formato de competência inválido (400)")
        void postFechamento_competenciaFormatoInvalido_retorna400BadRequest() throws Exception {
            String payload = """
                    {
                        "competencia": "2026-13",
                        "executadoPor": "auditor"
                    }
                    """;

            mockMvc.perform(post("/api/v1/comissoes/fechamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validacoes[0].motivo", containsString("Formato de competência inválido")));
        }

        @Test
        @DisplayName("1.5 - Deve rejeitar fechamento quando competência já possui fechamento CONCLUIDO (400)")
        void postFechamento_competenciaJaConcluida_retorna400BadRequest() throws Exception {
            FechamentoCompetencia f = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "auditor", "Primeiro fechamento");
            f.setStatus(StatusFechamento.CONCLUIDO);
            f.setConcluidoEm(OffsetDateTime.now());
            fechamentoCompetenciaRepository.save(f);

            FechamentoRequestDTO req = new FechamentoRequestDTO("2026-10", "outro@empresa.com", "Tentativa duplicada");

            mockMvc.perform(post("/api/v1/comissoes/fechamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("já possui fechamento final concluído")));
        }

        @Test
        @DisplayName("1.6 - Deve rejeitar fechamento quando competência já está em PROCESSANDO (400)")
        void postFechamento_competenciaEmProcessamento_retorna400BadRequest() throws Exception {
            FechamentoCompetencia f = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "auditor", "Em andamento");
            f.setStatus(StatusFechamento.PROCESSANDO);
            fechamentoCompetenciaRepository.save(f);

            FechamentoRequestDTO req = new FechamentoRequestDTO("2026-10", "outro@empresa.com", "Tentativa concorrente");

            mockMvc.perform(post("/api/v1/comissoes/fechamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("processo de fechamento em andamento")));
        }
    }

    @Nested
    @DisplayName("2. Rota GET /api/v1/comissoes/fechamento/{competencia}/status (Consultar Status)")
    class ConsultarStatusTests {

        @Test
        @DisplayName("2.1 - Deve retornar ABERTA e bloqueada=false para competência sem fechamentos")
        void getStatus_competenciaAberta_retorna200OkComBloqueadaFalse() throws Exception {
            mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-10/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.competencia").value("2026-10"))
                    .andExpect(jsonPath("$.status").value("ABERTA"))
                    .andExpect(jsonPath("$.bloqueada").value(false));
        }

        @Test
        @DisplayName("2.2 - Deve retornar CONCLUIDO e bloqueada=true com protocolo e id do fechamento")
        void getStatus_competenciaConcluida_retorna200OkComBloqueadaTrueEProtocolo() throws Exception {
            FechamentoCompetencia f = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "auditor@empresa.com", "Fechado");
            f.setStatus(StatusFechamento.CONCLUIDO);
            f.setConcluidoEm(OffsetDateTime.now());
            f = fechamentoCompetenciaRepository.save(f);

            mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-10/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.competencia").value("2026-10"))
                    .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.bloqueada").value(true))
                    .andExpect(jsonPath("$.fechamentoId").value(f.getId().toString()))
                    .andExpect(jsonPath("$.protocolo").value(f.getProtocolo().toString()));
        }

        @Test
        @DisplayName("2.3 - Deve retornar PROCESSANDO e bloqueada=true para fechamento em andamento")
        void getStatus_competenciaProcessando_retorna200OkComBloqueadaTrue() throws Exception {
            FechamentoCompetencia f = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "auditor", "Processando lote");
            f.setStatus(StatusFechamento.PROCESSANDO);
            fechamentoCompetenciaRepository.save(f);

            mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-10/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.competencia").value("2026-10"))
                    .andExpect(jsonPath("$.status").value("PROCESSANDO"))
                    .andExpect(jsonPath("$.bloqueada").value(true));
        }

        @Test
        @DisplayName("2.4 - Deve retornar FALHOU e bloqueada=false com motivo da falha liberando nova tentativa")
        void getStatus_competenciaFalhou_retorna200OkComBloqueadaFalseEMotivoFalha() throws Exception {
            FechamentoCompetencia f = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "auditor", "Falha");
            f.setStatus(StatusFechamento.FALHOU);
            f.setMotivoFalha("Tempo limite de processamento excedido");
            f.setConcluidoEm(OffsetDateTime.now());
            fechamentoCompetenciaRepository.save(f);

            mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-10/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.competencia").value("2026-10"))
                    .andExpect(jsonPath("$.status").value("FALHOU"))
                    .andExpect(jsonPath("$.bloqueada").value(false))
                    .andExpect(jsonPath("$.mensagem", containsString("Tempo limite de processamento excedido")));
        }

        @Test
        @DisplayName("2.5 - Deve retornar 400 Bad Request para formato de competência inválido na URL")
        void getStatus_formatoInvalido_retorna400BadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/comissoes/fechamento/invalido/status"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Formato de competência inválido")));
        }
    }

    @Nested
    @DisplayName("3. Rota GET /api/v1/comissoes/fechamento (Listar Fechamentos Paginados)")
    class ListarFechamentosTests {

        @Test
        @DisplayName("3.1 - Deve listar histórico paginado de fechamentos")
        void getHistorico_semFiltros_retorna200OkPaginado() throws Exception {
            FechamentoCompetencia f1 = new FechamentoCompetencia("2026-08", UUID.randomUUID(), "auditor", "Agosto");
            f1.setStatus(StatusFechamento.CONCLUIDO);
            fechamentoCompetenciaRepository.save(f1);

            FechamentoCompetencia f2 = new FechamentoCompetencia("2026-09", UUID.randomUUID(), "auditor", "Setembro");
            f2.setStatus(StatusFechamento.CONCLUIDO);
            fechamentoCompetenciaRepository.save(f2);

            mockMvc.perform(get("/api/v1/comissoes/fechamento"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("3.2 - Deve respeitar paginação customizada (size=1)")
        void getHistorico_comPaginacaoCustomizada_retorna200OkTamanhoEsperado() throws Exception {
            FechamentoCompetencia f1 = new FechamentoCompetencia("2026-08", UUID.randomUUID(), "auditor", "Agosto");
            f1.setStatus(StatusFechamento.CONCLUIDO);
            fechamentoCompetenciaRepository.save(f1);

            FechamentoCompetencia f2 = new FechamentoCompetencia("2026-09", UUID.randomUUID(), "auditor", "Setembro");
            f2.setStatus(StatusFechamento.CONCLUIDO);
            fechamentoCompetenciaRepository.save(f2);

            mockMvc.perform(get("/api/v1/comissoes/fechamento?page=0&size=1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("3.3 - Deve filtrar por competência existente")
        void getHistorico_filtradoPorCompetenciaExistente_retornaApenasFechamentosDaCompetencia() throws Exception {
            FechamentoCompetencia f1 = new FechamentoCompetencia("2026-08", UUID.randomUUID(), "auditor", "Agosto");
            f1.setStatus(StatusFechamento.CONCLUIDO);
            fechamentoCompetenciaRepository.save(f1);

            FechamentoCompetencia f2 = new FechamentoCompetencia("2026-09", UUID.randomUUID(), "auditor", "Setembro");
            f2.setStatus(StatusFechamento.CONCLUIDO);
            fechamentoCompetenciaRepository.save(f2);

            mockMvc.perform(get("/api/v1/comissoes/fechamento?competencia=2026-09"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].competencia").value("2026-09"));
        }

        @Test
        @DisplayName("3.4 - Deve retornar página vazia para competência sem fechamentos")
        void getHistorico_filtradoPorCompetenciaInexistente_retornaPaginaVazia() throws Exception {
            mockMvc.perform(get("/api/v1/comissoes/fechamento?competencia=1999-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("4. Bloqueio de Alterações e Publicações em Campanhas (/api/v1/campanhas)")
    class BloqueioCampanhasTests {

        private void fecharCompetenciaOutubro() {
            FechamentoCompetencia f = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "auditor", "Fechado");
            f.setStatus(StatusFechamento.CONCLUIDO);
            f.setConcluidoEm(OffsetDateTime.now());
            fechamentoCompetenciaRepository.save(f);
        }

        @Test
        @DisplayName("4.1 - Deve bloquear POST /api/v1/campanhas com vigência no mês fechado (400)")
        void bloqueioCampanhas_criarCampanhaEmMesFechado_retorna400BadRequest() throws Exception {
            fecharCompetenciaOutubro();

            CampanhaRequest req = new CampanhaRequest(
                    "Nova Campanha Outubro", "Texto",
                    LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 25),
                    EstadoCampanha.DRAFT, List.of(criarItemRegraPadrao(new BigDecimal("0.0500")))
            );

            mockMvc.perform(post("/api/v1/campanhas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("2026-10 está com fechamento final concluído")));
        }

        @Test
        @DisplayName("4.2 - Deve bloquear PUT /api/v1/campanhas/{id} para campanha no mês fechado (400)")
        void bloqueioCampanhas_atualizarCampanhaEmMesFechado_retorna400BadRequest() throws Exception {
            Campanha campanha = new Campanha("Campanha Outubro", "Texto", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
            campanha.setEstado(EstadoCampanha.DRAFT);
            campanha = campanhaRepository.save(campanha);

            fecharCompetenciaOutubro();

            CampanhaRequest updateReq = new CampanhaRequest(
                    "Campanha Editada", "Texto Editado",
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                    EstadoCampanha.DRAFT, List.of(criarItemRegraPadrao(new BigDecimal("0.0800")))
            );

            mockMvc.perform(put("/api/v1/campanhas/" + campanha.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("2026-10 está com fechamento final concluído")));
        }

        @Test
        @DisplayName("4.3 - Deve bloquear PATCH /api/v1/campanhas/{id}/estado para campanha no mês fechado (400)")
        void bloqueioCampanhas_alterarEstadoCampanhaEmMesFechado_retorna400BadRequest() throws Exception {
            Campanha campanha = new Campanha("Campanha Outubro", "Texto", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
            campanha.setEstado(EstadoCampanha.DRAFT);
            campanha = campanhaRepository.save(campanha);

            fecharCompetenciaOutubro();

            AlterarEstadoCampanhaRequest estadoReq = new AlterarEstadoCampanhaRequest(EstadoCampanha.INATIVA);

            mockMvc.perform(patch("/api/v1/campanhas/" + campanha.getId() + "/estado")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(estadoReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("2026-10 está com fechamento final concluído")));
        }

        @Test
        @DisplayName("4.4 - Deve bloquear POST /api/v1/campanhas/{id}/ativar para campanha no mês fechado (400)")
        void bloqueioCampanhas_ativarCampanhaEmMesFechado_retorna400BadRequest() throws Exception {
            Campanha campanha = new Campanha("Campanha Outubro", "Texto", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
            campanha.setEstado(EstadoCampanha.DRAFT);
            campanha = campanhaRepository.save(campanha);

            fecharCompetenciaOutubro();

            AtivarCampanhaRequest ativarReq = new AtivarCampanhaRequest(null);

            mockMvc.perform(post("/api/v1/campanhas/" + campanha.getId() + "/ativar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(ativarReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("2026-10 está com fechamento final concluído")));
        }

        @Test
        @DisplayName("4.5 - Deve bloquear DELETE /api/v1/campanhas/{id} para campanha no mês fechado (400)")
        void bloqueioCampanhas_removerCampanhaEmMesFechado_retorna400BadRequest() throws Exception {
            Campanha campanha = new Campanha("Campanha Outubro", "Texto", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
            campanha.setEstado(EstadoCampanha.DRAFT);
            campanha = campanhaRepository.save(campanha);

            fecharCompetenciaOutubro();

            mockMvc.perform(delete("/api/v1/campanhas/" + campanha.getId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("2026-10 está com fechamento final concluído")));
        }
    }

    @Nested
    @DisplayName("5. Bloqueio de Recálculos e Preservação de Consultas em /api/v1/comissoes")
    class BloqueioCalculosEPreservacaoConsultasTests {

        private void fecharCompetenciaOutubro() {
            FechamentoCompetencia f = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "auditor", "Fechado");
            f.setStatus(StatusFechamento.CONCLUIDO);
            f.setConcluidoEm(OffsetDateTime.now());
            fechamentoCompetenciaRepository.save(f);
        }

        @Test
        @DisplayName("5.1 - Deve bloquear recálculo forçado (recalcular=true) em competência fechada (400)")
        void bloqueioCalculos_recalcularCompetenciaFechada_retorna400BadRequest() throws Exception {
            fecharCompetenciaOutubro();

            CalculoCompetenciaRequest req = new CalculoCompetenciaRequest("2026-10", true);

            mockMvc.perform(post("/api/v1/comissoes/calcular-competencia")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("já possui fechamento final concluído e está bloqueada para recálculo")));
        }

        @Test
        @DisplayName("5.2 - Deve bloquear cálculo de nova venda individual avulsa em competência fechada (400)")
        void bloqueioCalculos_calcularNovaVendaEmCompetenciaFechada_retorna400BadRequest() throws Exception {
            fecharCompetenciaOutubro();

            Sale novaVenda = new Sale();
            novaVenda.setSaleDate(LocalDate.of(2026, 10, 15));
            novaVenda.setValue(new BigDecimal("1500.00"));
            novaVenda.setRegistration(registration);
            novaVenda.setBrand(brand);
            novaVenda.setStore(store);
            novaVenda = saleRepository.save(novaVenda);

            mockMvc.perform(post("/api/v1/comissoes/calcular/venda/" + novaVenda.getId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("possui fechamento final concluído e está bloqueada para novas apurações")));
        }

        @Test
        @DisplayName("5.3 - Deve preservar consulta histórica de venda já calculada previamente em competência fechada (200 OK)")
        void preservacaoConsultas_consultaVendaJaCalculadaEmMesFechado_retorna200Ok() throws Exception {
            Sale vendaCalculada = new Sale();
            vendaCalculada.setSaleDate(LocalDate.of(2026, 10, 10));
            vendaCalculada.setValue(new BigDecimal("2000.00"));
            vendaCalculada.setRegistration(registration);
            vendaCalculada.setBrand(brand);
            vendaCalculada.setStore(store);
            vendaCalculada = saleRepository.save(vendaCalculada);

            UUID protocolo = UUID.randomUUID();
            ResultadoCalculo resultado = new ResultadoCalculo(
                    protocolo,
                    vendaCalculada.getId(),
                    registration.getRegistration(),
                    brand.getCode(),
                    store.getCode(),
                    position.getCode(),
                    1L,
                    vendaCalculada.getSaleDate(),
                    vendaCalculada.getValue(),
                    new BigDecimal("0.0500"),
                    new BigDecimal("100.00"),
                    "FECHAMENTO_FINAL"
            );
            resultadoCalculoRepository.save(resultado);

            fecharCompetenciaOutubro();

            // Ao chamar o cálculo individual para a mesma venda, a idempotência retorna o resultado histórico sem erro
            mockMvc.perform(post("/api/v1/comissoes/calcular/venda/" + vendaCalculada.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.protocoloCalculo").value(protocolo.toString()))
                    .andExpect(jsonPath("$.valorVenda").value(2000.00))
                    .andExpect(jsonPath("$.valorComissao").value(100.00));
        }

        @Test
        @DisplayName("5.4 - Deve preservar consulta de competência fechada sem recálculo (recalcular=false) retornando resultados históricos (200 OK)")
        void preservacaoConsultas_calcularCompetenciaFechadaSemRecalcular_retorna200OkComResultadosPreservados() throws Exception {
            Sale venda = new Sale();
            venda.setSaleDate(LocalDate.of(2026, 10, 10));
            venda.setValue(new BigDecimal("1000.00"));
            venda.setRegistration(registration);
            venda.setBrand(brand);
            venda.setStore(store);
            venda = saleRepository.save(venda);

            ResultadoCalculo res = new ResultadoCalculo(
                    UUID.randomUUID(),
                    venda.getId(),
                    registration.getRegistration(),
                    brand.getCode(),
                    store.getCode(),
                    position.getCode(),
                    1L,
                    venda.getSaleDate(),
                    venda.getValue(),
                    new BigDecimal("0.0500"),
                    new BigDecimal("50.00"),
                    "FECHAMENTO_FINAL"
            );
            resultadoCalculoRepository.save(res);

            fecharCompetenciaOutubro();

            CalculoCompetenciaRequest req = new CalculoCompetenciaRequest("2026-10", false);

            mockMvc.perform(post("/api/v1/comissoes/calcular-competencia")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.competencia").value("2026-10"))
                    .andExpect(jsonPath("$.totalCalculados").value(1))
                    .andExpect(jsonPath("$.valorTotalComissao").value(50.00));
        }
    }
}
