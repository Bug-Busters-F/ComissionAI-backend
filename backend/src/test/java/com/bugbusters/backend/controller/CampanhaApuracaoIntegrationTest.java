package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.apuracao.CalculoDefinitivoCampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.esteira.DevolverCampanhaRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.pendencia.AceitarPendenciaRequest;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.AtualizarProgramaPythonRequest;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaApuracaoRepository;
import com.bugbusters.backend.repository.FechamentoCompetenciaRepository;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampanhaApuracaoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private CampanhaApuracaoRepository apuracaoRepository;

    @Autowired
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private LogCalculoRepository logCalculoRepository;

    @Autowired
    private FechamentoCompetenciaRepository fechamentoCompetenciaRepository;

    private Brand testBrand;
    private Store testStore;
    private Position testPosition;
    private Registration testRegistration;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        testBrand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("PRETO");
            return brandRepository.save(b);
        });

        testStore = storeRepository.findByCode(75).orElseGet(() -> {
            Store s = new Store();
            s.setCode(75);
            s.setDescription("LOJA TESTE APURACAO");
            return storeRepository.save(s);
        });

        testPosition = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR APURACAO");
            return positionRepository.save(p);
        });

        testRegistration = registrationRepository.findByRegistration("MATRIC-APUR-01").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-APUR-01");
            r.setPosition(testPosition);
            r.setStore(testStore);
            r.setAdmissDate(LocalDate.of(2020, 1, 1));
            return registrationRepository.save(r);
        });

        LocalDate mesRef = LocalDate.of(2026, 8, 1);
        baseComissRepository.findByBrandIdAndPositionIdAndReferenceMonth(testBrand.getId(), testPosition.getId(), mesRef)
                .orElseGet(() -> {
                    BaseComiss bc = new BaseComiss(testBrand, testPosition, new BigDecimal("0.0300"));
                    bc.setReferenceMonth(mesRef);
                    return baseComissRepository.save(bc);
                });

        Sale sale = new Sale();
        sale.setBrand(testBrand);
        sale.setStore(testStore);
        sale.setRegistration(testRegistration);
        sale.setSaleChannel("ECOMMERCE");
        sale.setSaleDate(LocalDate.of(2026, 8, 15));
        sale.setValue(new BigDecimal("10000.00"));
        saleRepository.save(sale);
    }

    private Long criarCampanhaTeste(String titulo) throws Exception {
        RegraItemRequest regra = new RegraItemRequest(
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
                "Texto base da campanha para teste de cálculo definitivo",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                EstadoCampanha.DRAFT,
                List.of(regra),
                null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private String aprovarProgramaPython(Long campanhaId) throws Exception {
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

        return checksum;
    }

    @Test
    @DisplayName("Deve rejeitar apuração definitiva se campanha não tiver programa Python aprovado")
    void deveRejeitarApuracaoDefinitivaSeNaoHouverProgramaPythonAprovado() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Sem Python Aprovado");

        CalculoDefinitivoCampanhaRequest calcReq = new CalculoDefinitivoCampanhaRequest(
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "gestor.financeiro",
                "Tentativa sem homologação técnica",
                false
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calcReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("programa Python homologado e aprovado")));
    }

    @Test
    @DisplayName("Deve rejeitar apuração definitiva se campanha estiver devolvida")
    void deveRejeitarApuracaoDefinitivaSeCampanhaEstiverDevolvida() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Devolvida");
        aprovarProgramaPython(campanhaId);

        // Devolve a campanha
        DevolverCampanhaRequest devReq = new DevolverCampanhaRequest(1, "Inconsistência nos percentuais", "gerente.comercial");
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/devolver", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devReq)))
                .andExpect(status().isOk());

        CalculoDefinitivoCampanhaRequest calcReq = new CalculoDefinitivoCampanhaRequest(
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "gestor.financeiro",
                "Tentativa em campanha devolvida",
                false
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calcReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("campanha está devolvida")));
    }

    @Test
    @DisplayName("Deve executar cálculo definitivo com sucesso, atualizar esteira e persistir resultados e logs")
    void deveExecutarApuracaoDefinitivaComSucessoEPersistirResultadosELogs() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Liquidação Definitiva");
        String checksum = aprovarProgramaPython(campanhaId);

        CalculoDefinitivoCampanhaRequest calcReq = new CalculoDefinitivoCampanhaRequest(
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "auditor.financeiro",
                "Cálculo definitivo de encerramento da campanha",
                false
        );

        MvcResult result = mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calcReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apuracaoId").isNotEmpty())
                .andExpect(jsonPath("$.protocolo").isNotEmpty())
                .andExpect(jsonPath("$.campanhaId").value(campanhaId))
                .andExpect(jsonPath("$.competencia").value("2026-08"))
                .andExpect(jsonPath("$.executadoViaPython").value(true))
                .andExpect(jsonPath("$.versaoProgramaPython").value(1))
                .andExpect(jsonPath("$.checksumPython").value(checksum))
                .andExpect(jsonPath("$.totalCalculados").value(1))
                .andExpect(jsonPath("$.totalImpedimentos").value(0))
                .andExpect(jsonPath("$.valorTotalVendas").value(10000.00))
                .andExpect(jsonPath("$.valorTotalComissao").value(500.00))
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].matricula").value("MATRIC-APUR-01"))
                .andExpect(jsonPath("$.itens[0].taxaAplicada").value(0.05))
                .andExpect(jsonPath("$.itens[0].valorComissao").value(500.00))
                .andReturn();

        JsonNode jsonResp = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID apuracaoId = UUID.fromString(jsonResp.get("apuracaoId").asText());

        // Verificação em banco: tb_campanha_apuracao
        CampanhaApuracao apuracaoSalva = apuracaoRepository.findById(apuracaoId).orElse(null);
        assertThat(apuracaoSalva).isNotNull();
        assertThat(apuracaoSalva.getStatus()).isEqualTo("CONCLUIDO");
        assertThat(apuracaoSalva.getValorTotalComissao()).isEqualByComparingTo("500.00");
        assertThat(apuracaoSalva.isExecutadoViaPython()).isTrue();

        // Verificação em banco: tb_resultado_calculo com vínculo e tipo CAMPANHA_DEFINITIVO
        List<ResultadoCalculo> resultados = resultadoCalculoRepository.findAllByApuracaoCampanhaIdOrderByDataVendaAsc(apuracaoId);
        assertThat(resultados).hasSize(1);
        ResultadoCalculo rc = resultados.get(0);
        assertThat(rc.getCampanhaId()).isEqualTo(campanhaId);
        assertThat(rc.getTipoCalculo()).isEqualTo("CAMPANHA_DEFINITIVO");
        assertThat(rc.getVersaoProgramaPython()).isEqualTo(1);
        assertThat(rc.getChecksumPython()).isEqualTo(checksum);
        assertThat(rc.getValorComissao()).isEqualByComparingTo("500.00");

        // Verificação em banco: tb_log_calculo_imutavel
        List<LogCalculoImutavel> logs = logCalculoRepository.findAll();
        boolean temLogCampanha = logs.stream()
                .anyMatch(l -> "CAMPANHA_DEFINITIVO".equals(l.getOrigemExecucao()) && "auditor.financeiro".equals(l.getUsuarioExecutor()));
        assertThat(temLogCampanha).isTrue();
    }

    @Test
    @DisplayName("Deve consultar última apuração definitiva e listar histórico da campanha")
    void deveConsultarUltimaApuracaoEHistorico() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Historico Apuracoes");
        aprovarProgramaPython(campanhaId);

        CalculoDefinitivoCampanhaRequest calcReq = new CalculoDefinitivoCampanhaRequest(
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "gestor.financeiro",
                "Execução para consulta posterior",
                false
        );

        MvcResult calcResult = mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calcReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonCalc = objectMapper.readTree(calcResult.getResponse().getContentAsString());
        String apuracaoId = jsonCalc.get("apuracaoId").asText();

        // 1. Consulta última apuração
        mockMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apuracaoId").value(apuracaoId))
                .andExpect(jsonPath("$.totalCalculados").value(1))
                .andExpect(jsonPath("$.valorTotalComissao").value(500.00));

        // 2. Consulta histórico
        mockMvc.perform(get("/api/v1/campanhas/{id}/apuracoes", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].apuracaoId").value(apuracaoId));

        // 3. Consulta por ID específico
        mockMvc.perform(get("/api/v1/campanhas/{id}/apuracoes/{apuracaoId}", campanhaId, apuracaoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apuracaoId").value(apuracaoId))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
    }

    @Test
    @DisplayName("Deve respeitar trava de fechamento formal se competência estiver concluída a menos que seja forçado")
    void deveRespeitarTravaDeFechamentoFormalSeCompetenciaConcluida() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha com Trava Fechamento");
        aprovarProgramaPython(campanhaId);

        // Insere fechamento prévio concluído para competência 2026-08
        FechamentoCompetencia fechamento = new FechamentoCompetencia("2026-08", UUID.randomUUID(), "controladoria", "Fechamento contábil");
        fechamento.setStatus(StatusFechamento.CONCLUIDO);
        fechamento.setConcluidoEm(OffsetDateTime.now());
        fechamentoCompetenciaRepository.save(fechamento);

        // Tentativa sem forçar recálculo deve ser bloqueada
        CalculoDefinitivoCampanhaRequest reqBloqueado = new CalculoDefinitivoCampanhaRequest(
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "gestor.financeiro",
                "Tentativa bloqueada por fechamento formal",
                false
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBloqueado)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("fechamento final formalizado")));

        // Tentativa com forcarRecalculo = true deve ser permitida
        CalculoDefinitivoCampanhaRequest reqForcado = new CalculoDefinitivoCampanhaRequest(
                "2026-08",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "gestor.financeiro",
                "Recálculo extraordinário homologado",
                true
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqForcado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCalculados").value(1));
    }
}

