package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.apuracao.CalculoDefinitivoCampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.esteira.AprovarGerenteRequest;
import com.bugbusters.backend.dto.esteira.AvancarEsteiraRequest;
import com.bugbusters.backend.dto.fechamento.FechamentoRequestDTO;
import com.bugbusters.backend.dto.fechamento.FechamentoResponseDTO;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.dto.simulacao.AceiteSimulacaoRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoPythonRequest;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.CampanhaVersao;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.FechamentoCompetencia;
import com.bugbusters.backend.model.ImpedimentoFechamento;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.StatusFechamento;
import com.bugbusters.backend.model.StatusVersaoCampanha;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.client.AiServiceClient;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.support.PythonSandboxMockSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "ai.service.url=http://localhost:8000",
        "spring.datasource.url=jdbc:h2:mem:relatorio_fechamento_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
public class RelatorioFechamentoIntegrationTest {

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
    private CampanhaApuracaoRepository apuracaoRepository;

    @Autowired
    private ImpedimentoFechamentoRepository impedimentoFechamentoRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate cleanupJdbc;

    @Autowired
    private RestClient.Builder aiRestClientBuilder;

    @Autowired
    private AiServiceClient aiServiceClient;

    private MockMvc mockMvc;
    private PythonSandboxMockSupport sandboxMock;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Brand testBrand;
    private Store testStore;
    private Position testPosition;
    private Registration testRegistration;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        sandboxMock = PythonSandboxMockSupport.instalar(aiRestClientBuilder, aiServiceClient)
                .registrarExecucaoPadrao();

        cleanupJdbc.update("DELETE FROM tb_log_calculo_imutavel");
        cleanupJdbc.update("DELETE FROM tb_resultado_calculo");
        cleanupJdbc.update("DELETE FROM tb_campanha_apuracao");
        cleanupJdbc.update("DELETE FROM tb_impedimento_fechamento");
        cleanupJdbc.update("DELETE FROM tb_fechamento_competencia");
        cleanupJdbc.update("DELETE FROM tb_campanha_pendencia_aceite");
        cleanupJdbc.update("DELETE FROM tb_campanha_esteira_transicao");
        cleanupJdbc.update("DELETE FROM tb_campanha_programa_python");
        com.bugbusters.backend.support.CampaignDataCleanup.clear(cleanupJdbc);
        saleRepository.deleteAll();
        baseComissRepository.deleteAll();

        testBrand = brandRepository.findByCode(20).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(20);
            b.setDescription("MARCA RELATORIO");
            return brandRepository.save(b);
        });

        testStore = storeRepository.findByCode(80).orElseGet(() -> {
            Store s = new Store();
            s.setCode(80);
            s.setDescription("LOJA SHOPPING NORTE");
            return storeRepository.save(s);
        });

        testPosition = positionRepository.findByCode(200).orElseGet(() -> {
            Position p = new Position();
            p.setCode(200);
            p.setDescription("CONSULTOR DE VENDAS");
            return positionRepository.save(p);
        });

        testRegistration = registrationRepository.findByRegistration("MATRIC-REL-10").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-REL-10");
            r.setPosition(testPosition);
            r.setStore(testStore);
            r.setAdmissDate(LocalDate.of(2021, 5, 1));
            return registrationRepository.save(r);
        });

        LocalDate mesRef = LocalDate.of(2026, 10, 1);
        baseComissRepository.findByBrandIdAndPositionIdAndReferenceMonth(testBrand.getId(), testPosition.getId(), mesRef)
                .orElseGet(() -> {
                    BaseComiss bc = new BaseComiss(testBrand, testPosition, new BigDecimal("0.0400"));
                    bc.setReferenceMonth(mesRef);
                    return baseComissRepository.save(bc);
                });
    }

    @AfterEach
    void tearDown() {
        if (sandboxMock != null) {
            sandboxMock.restaurar();
        }
    }

    @Test
    @DisplayName("FRONT-11 / BACK-15: Deve consolidar totais SQL, aplicar filtros combinados e paginar impedimentos persistidos")
    void deveAplicarFiltrosNoRelatorioEListarImpedimentosPaginadosPorMatriculaEMarca() throws Exception {
        String competencia = "2026-10";
        LocalDate inicioMes = LocalDate.of(2026, 10, 1);
        LocalDate fimMes = LocalDate.of(2026, 10, 31);

        Brand marca2 = brandRepository.findByCode(21).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(21);
            b.setDescription("MARCA SUL");
            return brandRepository.save(b);
        });
        Store loja2 = storeRepository.findByCode(81).orElseGet(() -> {
            Store s = new Store();
            s.setCode(81);
            s.setDescription("LOJA SHOPPING SUL");
            return storeRepository.save(s);
        });
        LocalDate mesRef = LocalDate.of(2026, 10, 1);
        baseComissRepository.findByBrandIdAndPositionIdAndReferenceMonth(marca2.getId(), testPosition.getId(), mesRef)
                .orElseGet(() -> {
                    BaseComiss bc = new BaseComiss(marca2, testPosition, new BigDecimal("0.0400"));
                    bc.setReferenceMonth(mesRef);
                    return baseComissRepository.save(bc);
                });

        Registration regColab2 = registrationRepository.findByRegistration("MATRIC-REL-20").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-REL-20");
            r.setPosition(testPosition);
            r.setStore(loja2);
            r.setAdmissDate(LocalDate.of(2022, 1, 10));
            return registrationRepository.save(r);
        });
        Registration regImp1 = registrationRepository.findByRegistration("MATRIC-IMP-20").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-IMP-20");
            r.setPosition(testPosition);
            r.setStore(testStore);
            r.setAdmissDate(LocalDate.of(2026, 10, 20));
            return registrationRepository.save(r);
        });
        Registration regImp2 = registrationRepository.findByRegistration("MATRIC-IMP-21").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-IMP-21");
            r.setPosition(testPosition);
            r.setStore(loja2);
            r.setAdmissDate(LocalDate.of(2022, 1, 10));
            r.setDemissDate(LocalDate.of(2026, 10, 5));
            return registrationRepository.save(r);
        });

        // 2 vendas calculadas em marcas/lojas distintas + 2 vendas com impedimento de vigência cadastral (admissão posterior / demissão anterior)
        Sale vendaCalc1 = new Sale();
        vendaCalc1.setSaleDate(LocalDate.of(2026, 10, 5));
        vendaCalc1.setValue(new BigDecimal("4000.00"));
        vendaCalc1.setRegistration(testRegistration);
        vendaCalc1.setBrand(testBrand);
        vendaCalc1.setStore(testStore);
        vendaCalc1.setSaleChannel("ECOMMERCE");
        saleRepository.save(vendaCalc1);

        Sale vendaCalc2 = new Sale();
        vendaCalc2.setSaleDate(LocalDate.of(2026, 10, 8));
        vendaCalc2.setValue(new BigDecimal("2000.00"));
        vendaCalc2.setRegistration(regColab2);
        vendaCalc2.setBrand(marca2);
        vendaCalc2.setStore(loja2);
        vendaCalc2.setSaleChannel("LOJA_FISICA");
        saleRepository.save(vendaCalc2);

        Sale vendaImp1 = new Sale();
        vendaImp1.setSaleDate(LocalDate.of(2026, 10, 12));
        vendaImp1.setValue(new BigDecimal("1500.00"));
        vendaImp1.setRegistration(regImp1);
        vendaImp1.setBrand(testBrand);
        vendaImp1.setStore(testStore);
        vendaImp1.setSaleChannel("ECOMMERCE");
        saleRepository.save(vendaImp1);

        Sale vendaImp2 = new Sale();
        vendaImp2.setSaleDate(LocalDate.of(2026, 10, 14));
        vendaImp2.setValue(new BigDecimal("2500.00"));
        vendaImp2.setRegistration(regImp2);
        vendaImp2.setBrand(marca2);
        vendaImp2.setStore(loja2);
        vendaImp2.setSaleChannel("LOJA_FISICA");
        saleRepository.save(vendaImp2);

        Campanha campanha = new Campanha();
        campanha.setTitulo("Campanha Multi Marca FRONT-11");
        campanha.setTextoOriginal("Regras por marca");
        campanha.setDataInicio(inicioMes);
        campanha.setDataFim(fimMes);
        campanha.setEstado(EstadoCampanha.ATIVA);
        campanha = campanhaRepository.save(campanha);

        CampanhaVersao versao = new CampanhaVersao();
        versao.setCampanha(campanha);
        versao.setNumero(1);
        versao.setStatus(StatusVersaoCampanha.PUBLICADA);
        versao.setDataInicio(inicioMes);
        versao.setDataFim(fimMes);
        versao.setTitulo("Campanha Multi Marca v1");
        versao.setTextoOriginal("Regras por marca");
        versao.setPublicadoEm(OffsetDateTime.now());
        versao = campanhaVersaoRepository.save(versao);

        campanha.setVersaoVigente(versao);
        campanhaRepository.save(campanha);

        Regra regraA = new Regra();
        regraA.setCampanha(campanha);
        regraA.setCampanhaVersao(versao);
        regraA.setNome("Regra Marca Norte 10%");
        regraA.setTaxa(new BigDecimal("0.1000"));
        regraA.setStatus(StatusRegra.ATIVA);
        regraA.setCompleta(true);
        regraA.setDataInicio(inicioMes);
        regraA.setDataFim(fimMes);
        regraA.setCodMarca(testBrand.getCode());
        regraA.setCodCargo(testPosition.getCode());
        regraA = regraRepository.save(regraA);

        Regra regraB = new Regra();
        regraB.setCampanha(campanha);
        regraB.setCampanhaVersao(versao);
        regraB.setNome("Regra Marca Sul 15%");
        regraB.setTaxa(new BigDecimal("0.1500"));
        regraB.setStatus(StatusRegra.ATIVA);
        regraB.setCompleta(true);
        regraB.setDataInicio(inicioMes);
        regraB.setDataFim(fimMes);
        regraB.setCodMarca(marca2.getCode());
        regraB.setCodCargo(testPosition.getCode());
        regraB = regraRepository.save(regraB);

        FechamentoRequestDTO request = new FechamentoRequestDTO(competencia, "controladoria@empresa.com", "Liquidação Outubro FRONT-11");
        MvcResult fechamentoResult = mockMvc.perform(post("/api/v1/comissoes/fechamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andReturn();
        String fechamentoIdStr = objectMapper.readTree(fechamentoResult.getResponse().getContentAsString()).get("id").asText();

        // 1. Relatório sem filtros com contrato unificado FRONT-11 + campos legados
        mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/relatorio", competencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia").value(competencia))
                .andExpect(jsonPath("$.fechamentoId").value(fechamentoIdStr))
                .andExpect(jsonPath("$.protocolo").isNotEmpty())
                .andExpect(jsonPath("$.fechadoEm").isNotEmpty())
                .andExpect(jsonPath("$.executadoPor").value("controladoria@empresa.com"))
                .andExpect(jsonPath("$.motivo").value("Liquidação Outubro FRONT-11"))
                .andExpect(jsonPath("$.totaisGerais.totalVendasProcessadas").value(4))
                .andExpect(jsonPath("$.totaisGerais.totalCalculados").value(2))
                .andExpect(jsonPath("$.totaisGerais.totalImpedimentos").value(2))
                .andExpect(jsonPath("$.totaisGerais.valorTotalVendas").value(6000.00))
                .andExpect(jsonPath("$.totaisGerais.valorTotalComissao").value(700.00))
                .andExpect(jsonPath("$.totaisPorRegra", hasSize(2)))
                .andExpect(jsonPath("$.totaisPorColaborador", hasSize(2)))
                .andExpect(jsonPath("$.detalheVendas", hasSize(2)));

        // 2. Filtros combinados por codLoja, codMarca e matricula
        mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/relatorio", competencia)
                        .param("codLoja", "80")
                        .param("codMarca", "20")
                        .param("matricula", "MATRIC-REL-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totaisGerais.totalCalculados").value(1))
                .andExpect(jsonPath("$.totaisGerais.totalImpedimentos").value(0))
                .andExpect(jsonPath("$.totaisGerais.valorTotalVendas").value(4000.00))
                .andExpect(jsonPath("$.totaisGerais.valorTotalComissao").value(400.00))
                .andExpect(jsonPath("$.totaisPorRegra", hasSize(1)))
                .andExpect(jsonPath("$.totaisPorRegra[0].idRegra").value(regraA.getId()))
                .andExpect(jsonPath("$.totaisPorColaborador", hasSize(1)))
                .andExpect(jsonPath("$.totaisPorColaborador[0].matricula").value("MATRIC-REL-10"))
                .andExpect(jsonPath("$.detalheVendas", hasSize(1)));

        // 2b. Filtros por codLoja e codMarca contabilizam tanto calculados quanto impedimentos daquele recorte
        mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/relatorio", competencia)
                        .param("codLoja", "80")
                        .param("codMarca", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totaisGerais.totalCalculados").value(1))
                .andExpect(jsonPath("$.totaisGerais.totalImpedimentos").value(1))
                .andExpect(jsonPath("$.totaisGerais.totalVendasProcessadas").value(2));

        // 3. Filtro por idRegra específica
        mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/relatorio", competencia)
                        .param("idRegra", String.valueOf(regraB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totaisGerais.totalCalculados").value(1))
                .andExpect(jsonPath("$.totaisGerais.valorTotalVendas").value(2000.00))
                .andExpect(jsonPath("$.totaisGerais.valorTotalComissao").value(300.00))
                .andExpect(jsonPath("$.totaisPorColaborador", hasSize(1)))
                .andExpect(jsonPath("$.totaisPorColaborador[0].matricula").value("MATRIC-REL-20"));

        // 4. Listagem paginada de impedimentos persistidos em tb_impedimento_fechamento
        mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/impedimentos", competencia)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].tipoImpedimento").value("COLABORADOR_DEMISSAO_ANTERIOR"))
                .andExpect(jsonPath("$.content[0].nomeColaborador", startsWith("Colaborador ")));

        mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/impedimentos", competencia)
                        .param("codMarca", "21")
                        .param("matricula", "MATRIC-IMP-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].matricula").value("MATRIC-IMP-21"))
                .andExpect(jsonPath("$.content[0].nomeColaborador").value("Colaborador MATRIC-IMP-21"))
                .andExpect(jsonPath("$.content[0].valorVenda").value(2500.00));

        // 5. Fallback para matricula ausente em tb_registration
        ImpedimentoFechamento impOrfa = new ImpedimentoFechamento(
                UUID.randomUUID(),
                UUID.fromString(fechamentoIdStr),
                competencia,
                UUID.randomUUID(),
                "MATRIC-ORFA",
                21,
                LocalDate.of(2026, 10, 18),
                new BigDecimal("500.00"),
                "Colaborador sem cadastro ativo",
                "COLABORADOR_NAO_ENCONTRADO"
        );
        impedimentoFechamentoRepository.save(impOrfa);

        mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/impedimentos", competencia)
                        .param("matricula", "MATRIC-ORFA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].nomeColaborador").value("Colaborador Não Encontrado"));
    }

    @Test
    @DisplayName("FRONT-11 / BACK-15: Deve retornar 404 estrito para fechamento não concluído e 400 para competência/parâmetro inválido")
    void deveValidarFormatoCompetenciaEExigirFechamentoConcluidoStrict404() throws Exception {
        FechamentoCompetencia emAndamento = new FechamentoCompetencia("2026-08", UUID.randomUUID(), "analista", "Em curso");
        emAndamento.setStatus(StatusFechamento.PROCESSANDO);
        fechamentoCompetenciaRepository.save(emAndamento);

        UUID protocoloFalhou = UUID.randomUUID();
        FechamentoCompetencia falhou = new FechamentoCompetencia("2026-09", protocoloFalhou, "analista", "Falhou no motor");
        falhou.setStatus(StatusFechamento.FALHOU);
        fechamentoCompetenciaRepository.save(falhou);

        mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-08/relatorio"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-09/relatorio"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-08/impedimentos"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/comissoes/fechamento/protocolo/{protocolo}/relatorio", protocoloFalhou))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-11/relatorio"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-13/relatorio"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/comissoes/fechamento/invalida/impedimentos"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-10/impedimentos")
                        .param("codMarca", "nao-numerico"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("BUG-110 / BACK-15: Deve consolidar relatório por competência (canal, equipe, colaborador) e exportar CSV UTF-8 com BOM")
    void deveConsolidarRelatorioCompetenciaEExportarCsv() throws Exception {
        String competencia = "2026-10";
        LocalDate inicioMes = LocalDate.of(2026, 10, 1);
        LocalDate fimMes = LocalDate.of(2026, 10, 31);

        // 1. Criar vendas no período com canais distintos
        Sale sale1 = new Sale();
        sale1.setSaleDate(LocalDate.of(2026, 10, 10));
        sale1.setValue(new BigDecimal("5000.00"));
        sale1.setRegistration(testRegistration);
        sale1.setBrand(testBrand);
        sale1.setStore(testStore);
        sale1.setSaleChannel("ECOMMERCE");
        saleRepository.save(sale1);

        Sale sale2 = new Sale();
        sale2.setSaleDate(LocalDate.of(2026, 10, 20));
        sale2.setValue(new BigDecimal("3000.00"));
        sale2.setRegistration(testRegistration);
        sale2.setBrand(testBrand);
        sale2.setStore(testStore);
        sale2.setSaleChannel("LOJA_FISICA");
        saleRepository.save(sale2);

        // 2. Criar campanha aprovada para a competência
        Campanha campanha = new Campanha();
        campanha.setTitulo("Campanha Outubro 2026");
        campanha.setTextoOriginal("Comissão 12% em todas as vendas");
        campanha.setDataInicio(inicioMes);
        campanha.setDataFim(fimMes);
        campanha.setEstado(EstadoCampanha.ATIVA);
        campanha = campanhaRepository.save(campanha);

        CampanhaVersao versao2 = new CampanhaVersao();
        versao2.setCampanha(campanha);
        versao2.setNumero(2);
        versao2.setStatus(StatusVersaoCampanha.PUBLICADA);
        versao2.setDataInicio(inicioMes);
        versao2.setDataFim(fimMes);
        versao2.setTitulo("Campanha Outubro 2026 - v2");
        versao2.setTextoOriginal("Comissão 12% em todas as vendas");
        versao2.setPublicadoEm(OffsetDateTime.now());
        versao2 = campanhaVersaoRepository.save(versao2);

        campanha.setVersaoVigente(versao2);
        campanhaRepository.save(campanha);

        Regra regraVersao2 = new Regra();
        regraVersao2.setCampanha(campanha);
        regraVersao2.setCampanhaVersao(versao2);
        regraVersao2.setNome("Regra v2 - 12%");
        regraVersao2.setTaxa(new BigDecimal("0.1200"));
        regraVersao2.setStatus(StatusRegra.ATIVA);
        regraVersao2.setCompleta(true);
        regraVersao2.setDataInicio(inicioMes);
        regraVersao2.setDataFim(fimMes);
        regraVersao2.setCodMarca(testBrand.getCode());
        regraVersao2.setCodCargo(testPosition.getCode());
        regraRepository.save(regraVersao2);

        // 3. Executar o fechamento oficial da competência
        FechamentoRequestDTO request = new FechamentoRequestDTO(competencia, "auditor.fiscal", "Fechamento mensal oficial");
        MvcResult fechamentoResult = mockMvc.perform(post("/api/v1/comissoes/fechamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andReturn();

        FechamentoResponseDTO fechamentoResponse = objectMapper.readValue(
                fechamentoResult.getResponse().getContentAsString(),
                FechamentoResponseDTO.class
        );
        UUID protocolo = fechamentoResponse.protocolo();
        assertThat(protocolo).isNotNull();

        // 4. Testar Endpoint JSON de Relatório Consolidado por Competência
        mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/relatorio", competencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.identificador", containsString("Competência 2026-10")))
                .andExpect(jsonPath("$.resumo.competencia").value("2026-10"))
                .andExpect(jsonPath("$.resumo.protocolo").value(protocolo.toString()))
                .andExpect(jsonPath("$.resumo.totalVendasProcessadas").value(2))
                .andExpect(jsonPath("$.resumo.valorTotalVendas").value(8000.00))
                .andExpect(jsonPath("$.resumo.totalColaboradoresDistintos").value(1))
                .andExpect(jsonPath("$.resumo.totalLojasDistintas").value(1))
                .andExpect(jsonPath("$.resumo.totalCanaisDistintos").value(2))
                .andExpect(jsonPath("$.porCanal", hasSize(2)))
                .andExpect(jsonPath("$.porEquipe", hasSize(1)))
                .andExpect(jsonPath("$.porColaborador", hasSize(1)))
                .andExpect(jsonPath("$.porColaborador[0].matricula").value("MATRIC-REL-10"))
                .andExpect(jsonPath("$.detalheVendas", hasSize(2)));

        // 5. Testar Endpoint de Relatório por Protocolo
        mockMvc.perform(get("/api/v1/comissoes/fechamento/protocolo/{protocolo}/relatorio", protocolo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.protocolo").value(protocolo.toString()))
                .andExpect(jsonPath("$.resumo.totalVendasProcessadas").value(2));

        // 6. Testar Exportação CSV Detalhado com BOM UTF-8
        MvcResult csvDetalhadoResult = mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/relatorio/exportar", competencia)
                        .param("tipo", "DETALHADO")
                        .param("delimitador", ";"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("relatorio_fechamento_2026-10_detalhado.csv")))
                .andReturn();

        byte[] csvBytes = csvDetalhadoResult.getResponse().getContentAsByteArray();
        assertThat(csvBytes.length).isGreaterThan(3);
        // Verifica UTF-8 BOM (\uFEFF -> 0xEF, 0xBB, 0xBF)
        assertThat(csvBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(csvBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(csvBytes[2]).isEqualTo((byte) 0xBF);

        String csvText = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csvText).contains("ProtocoloCalculo;IdVenda;DataVenda;Matricula;CodLoja;NomeLoja");
        assertThat(csvText).contains("ECOMMERCE");
        assertThat(csvText).contains("LOJA_FISICA");
        assertThat(csvText).contains("MATRIC-REL-10");

        // 7. Testar Exportação CSV Consolidado por Canal
        MvcResult csvCanalResult = mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/relatorio/exportar", competencia)
                        .param("tipo", "CONSOLIDADO_CANAL")
                        .param("delimitador", ";"))
                .andExpect(status().isOk())
                .andReturn();

        String csvCanalText = new String(csvCanalResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csvCanalText).contains("Canal;QtdVendas;QtdColaboradores;ValorTotalVendas;ValorTotalComissao");
        assertThat(csvCanalText).contains("ECOMMERCE;1;1;5000.00");
        assertThat(csvCanalText).contains("LOJA_FISICA;1;1;3000.00");

        // 8. Testar Exportação CSV Consolidado por Equipe
        MvcResult csvEquipeResult = mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/relatorio/exportar", competencia)
                        .param("tipo", "CONSOLIDADO_EQUIPE")
                        .param("delimitador", ";"))
                .andExpect(status().isOk())
                .andReturn();

        String csvEquipeText = new String(csvEquipeResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csvEquipeText).contains("CodLoja;NomeLoja;CodMarca;NomeMarca");
        assertThat(csvEquipeText).contains("80;LOJA SHOPPING NORTE;20;MARCA RELATORIO");
        assertThat(csvEquipeText).contains("8000.00");

        // 9. Testar Exportação CSV Consolidado por Colaborador
        MvcResult csvColabResult = mockMvc.perform(get("/api/v1/comissoes/fechamento/{competencia}/relatorio/exportar", competencia)
                        .param("tipo", "CONSOLIDADO_COLABORADOR")
                        .param("delimitador", ";"))
                .andExpect(status().isOk())
                .andReturn();

        String csvColabText = new String(csvColabResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csvColabText).contains("Matricula;CodCargo;NomeCargo;CodLoja;NomeLoja");
        assertThat(csvColabText).contains("MATRIC-REL-10;200;CONSULTOR DE VENDAS;80;LOJA SHOPPING NORTE;20;MARCA RELATORIO;2;8000.00");

        // 10. Testar Exportação CSV por Protocolo
        mockMvc.perform(get("/api/v1/comissoes/fechamento/protocolo/{protocolo}/relatorio/exportar", protocolo)
                        .param("tipo", "COMPLETO"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(protocolo.toString())));
    }

    @Test
    @DisplayName("BUG-110 / BACK-15: Deve consolidar relatório e exportar CSV de apuração definitiva por campanha")
    void deveConsolidarRelatorioCampanhaEExportarCsv() throws Exception {
        // 1. Criar venda para a campanha
        Sale sale = new Sale();
        sale.setSaleDate(LocalDate.of(2026, 10, 15));
        sale.setValue(new BigDecimal("12000.00"));
        sale.setRegistration(testRegistration);
        sale.setBrand(testBrand);
        sale.setStore(testStore);
        sale.setSaleChannel("ECOMMERCE");
        saleRepository.save(sale);

        // 2. Criar campanha
        Long campanhaId = criarCampanhaTeste("Campanha Apuracao Relatorio Test");

        // 3. Avançar esteira até EM_REVISAO_TECNICA (etapa 2)
        avancarEsteira(campanhaId, 2);

        // 4. Homologar programa Python na etapa 2 (transita para etapa 3)
        aprovarProgramaPython(campanhaId);

        // 5. Simular, aceitar simulação, avançar esteira para etapa 4 e aprovar gerencialmente
        SimulacaoPythonRequest simReq = new SimulacaoPythonRequest("2026-10", null, 50, 0, false, false);
        MvcResult simRes = mockMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID simId = UUID.fromString(objectMapper.readTree(simRes.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(post("/api/v1/simulacoes/{id}/aceite", simId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AceiteSimulacaoRequest("gestor.relatorio", "Aceite para relatório"))))
                .andExpect(status().isOk());

        avancarEsteira(campanhaId, 4);

        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarGerenteRequest("gerente.comercial", "Aprovação gerencial para relatório"))))
                .andExpect(status().isOk());

        // 6. Executar cálculo definitivo da campanha
        CalculoDefinitivoCampanhaRequest calcReq = new CalculoDefinitivoCampanhaRequest(
                "2026-10",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                "gerente.comercial",
                "Apuracao definitiva para relatorio",
                false
        );
        MvcResult apurResult = mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calcReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andReturn();

        JsonNode apurJson = objectMapper.readTree(apurResult.getResponse().getContentAsString());
        UUID apuracaoId = UUID.fromString(apurJson.get("apuracaoId").asText());

        // 7. Testar Relatório da última apuração da campanha
        mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva/relatorio", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.identificador", containsString("Campanha Apuracao Relatorio Test")))
                .andExpect(jsonPath("$.resumo.totalVendasProcessadas").value(1))
                .andExpect(jsonPath("$.resumo.valorTotalVendas").value(12000.00))
                .andExpect(jsonPath("$.porCanal", hasSize(1)))
                .andExpect(jsonPath("$.porCanal[0].canal").value("ECOMMERCE"));

        // 8. Testar Exportação CSV da última apuração da campanha
        MvcResult csvResult = mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva/exportar", campanhaId)
                        .param("tipo", "DETALHADO"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("relatorio_campanha_" + campanhaId + "_detalhado.csv")))
                .andReturn();

        byte[] csvBytes = csvResult.getResponse().getContentAsByteArray();
        assertThat(csvBytes.length).isGreaterThan(3);
        assertThat(csvBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(csvBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(csvBytes[2]).isEqualTo((byte) 0xBF);

        String csvText = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csvText).contains("ECOMMERCE");
        assertThat(csvText).contains("MATRIC-REL-10");

        // 9. Testar Relatório de apuração específica pelo apuracaoId
        mockMvc.perform(get("/api/v1/campanhas/{id}/apuracoes/{apuracaoId}/relatorio", campanhaId, apuracaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.protocolo").value(apurJson.get("protocolo").asText()));

        // 9. Testar Exportação CSV de apuração específica
        mockMvc.perform(get("/api/v1/campanhas/{id}/apuracoes/{apuracaoId}/exportar", campanhaId, apuracaoId)
                        .param("tipo", "CONSOLIDADO_CANAL"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("apuracao_" + apuracaoId)));
    }

    private Long criarCampanhaTeste(String titulo) throws Exception {
        RegraItemRequest regra = new RegraItemRequest(
                "bloco-1",
                "Comissão de 5% no canal ecommerce",
                "Regra E-commerce 5%",
                "ECOMMERCE",
                20, "MARCA RELATORIO", null, null, null, null,
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
                "Descrição teste relatório",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                EstadoCampanha.DRAFT,
                List.of(regra),
                null,
                new BigDecimal("50000.00"),
                null,
                true,
                "gestor.relatorio",
                "Orçamento aprovado para relatório"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private void avancarEsteira(Long campanhaId, int proximoEstado) throws Exception {
        AvancarEsteiraRequest request = new AvancarEsteiraRequest(proximoEstado, "revisor.esteira");
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void aprovarProgramaPython(Long campanhaId) throws Exception {
        MvcResult resProg = mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonProg = objectMapper.readTree(resProg.getResponse().getContentAsString());
        String checksum = jsonProg.get("checksumSha256").asText();

        AprovarProgramaPythonRequest aprovRequest = new AprovarProgramaPythonRequest(
                "revisor.tecnico",
                checksum,
                "Programa Python homologado e aprovado para cálculo financeiro"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aprovRequest)))
                .andExpect(status().isOk());
    }
}
