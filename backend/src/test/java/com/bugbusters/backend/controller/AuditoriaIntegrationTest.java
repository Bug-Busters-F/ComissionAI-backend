package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.auditoria.RegistroAuditoriaDTO;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.esteira.AvancarEsteiraRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.AuditoriaGeralRepository;
import com.bugbusters.backend.repository.CampanhaProgramaPythonRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.CampanhaVersaoRepository;
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
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "ai.service.url=http://localhost:8000",
        "spring.datasource.url=jdbc:h2:mem:auditoria_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
public class AuditoriaIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuditoriaGeralRepository auditoriaRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private CampanhaVersaoRepository campanhaVersaoRepository;

    @Autowired
    private CampanhaProgramaPythonRepository programaPythonRepository;

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
    private org.springframework.jdbc.core.JdbcTemplate cleanupJdbc;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        cleanupJdbc.update("DELETE FROM tb_auditoria_geral");
        cleanupJdbc.update("DELETE FROM tb_campanha_apuracao");
        cleanupJdbc.update("DELETE FROM tb_campanha_pendencia_aceite");
        cleanupJdbc.update("DELETE FROM tb_campanha_esteira_transicao");
        cleanupJdbc.update("DELETE FROM tb_campanha_programa_python");
        com.bugbusters.backend.support.CampaignDataCleanup.clear(cleanupJdbc);
    }

    @Test
    @DisplayName("BUG-111 / BACK-16: Deve registrar ação auditável manualmente via endpoint REST")
    void deveRegistrarAcaoDeAuditoriaManualmente() throws Exception {
        RegistroAuditoriaDTO request = new RegistroAuditoriaDTO(
                "compliance.auditor@empresa.com",
                "AUDITOR",
                TipoAcaoAuditoria.ACAO_OPERACIONAL,
                TipoEntidadeAuditoria.GERAL,
                "SISTEMA-01",
                null,
                "Auditoria manual de conformidade executada no ambiente",
                null,
                "{\"status\": \"EM_CONFORMIDADE\"}",
                "127.0.0.1",
                "JUnit Test Runner",
                true,
                null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auditoria")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocolo").isNotEmpty())
                .andExpect(jsonPath("$.usuario").value("compliance.auditor@empresa.com"))
                .andExpect(jsonPath("$.tipoAcao").value("ACAO_OPERACIONAL"))
                .andExpect(jsonPath("$.tipoEntidade").value("GERAL"))
                .andExpect(jsonPath("$.sucesso").value(true))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID protocolo = UUID.fromString(json.get("protocolo").asText());

        assertThat(auditoriaRepository.findByProtocolo(protocolo)).isPresent();
    }

    @Test
    @DisplayName("BUG-111 / BACK-16: Deve listar auditorias com filtros dinâmicos e paginação")
    void deveListarAuditoriaComFiltrosEPaginacao() throws Exception {
        // Cria registros para teste
        auditoriaRepository.save(new AuditoriaGeral(
                "usuario.a@empresa.com", "OPERADOR", TipoAcaoAuditoria.EDICAO_CAMPANHA,
                TipoEntidadeAuditoria.CAMPANHA, "10", 1, "Edição de regras da campanha",
                null, null, "192.168.0.1", "Browser", true, null
        ));

        auditoriaRepository.save(new AuditoriaGeral(
                "usuario.b@empresa.com", "AUDITOR", TipoAcaoAuditoria.APROVACAO_PROGRAMA_PYTHON,
                TipoEntidadeAuditoria.PROGRAMA_PYTHON, "10", 1, "Aprovação do código Python",
                null, null, "192.168.0.2", "Browser", true, null
        ));

        auditoriaRepository.save(new AuditoriaGeral(
                "usuario.a@empresa.com", "OPERADOR", TipoAcaoAuditoria.DEVOLUCAO_ESTEIRA,
                TipoEntidadeAuditoria.ESTEIRA, "10", 1, "Devolução por inconsistência",
                null, null, "192.168.0.1", "Browser", false, "Regra ambígua"
        ));

        // 1. Filtro por usuário
        mockMvc.perform(get("/api/v1/auditoria")
                        .param("usuario", "usuario.b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].usuario").value("usuario.b@empresa.com"));

        // 2. Filtro por tipo de ação
        mockMvc.perform(get("/api/v1/auditoria")
                        .param("tipoAcao", "EDICAO_CAMPANHA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipoAcao").value("EDICAO_CAMPANHA"));

        // 3. Filtro por sucesso
        mockMvc.perform(get("/api/v1/auditoria")
                        .param("sucesso", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sucesso").value(false));
    }

    @Test
    @DisplayName("BUG-111 / BACK-16: Deve buscar registro por protocolo e por entidade")
    void deveBuscarPorProtocoloEPorEntidade() throws Exception {
        AuditoriaGeral audit = new AuditoriaGeral(
                "gerente.vendas@empresa.com", "GERENTE", TipoAcaoAuditoria.AVANCO_ESTEIRA,
                TipoEntidadeAuditoria.ESTEIRA, "99", 2, "Avanço para homologação",
                null, null, "10.0.0.1", "Client", true, null
        );
        audit = auditoriaRepository.save(audit);

        // Busca por protocolo
        mockMvc.perform(get("/api/v1/auditoria/protocolo/{protocolo}", audit.getProtocolo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocolo").value(audit.getProtocolo().toString()))
                .andExpect(jsonPath("$.descricao").value("Avanço para homologação"));

        // Busca por entidade
        mockMvc.perform(get("/api/v1/auditoria/entidades/ESTEIRA/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entidadeId").value("99"));
    }

    @Test
    @DisplayName("BUG-111 / BACK-16: Deve obter resumo e métricas executivas da auditoria")
    void deveObterResumoEMetricasDeAuditoria() throws Exception {
        auditoriaRepository.save(new AuditoriaGeral(
                "u1@empresa.com", "USER", TipoAcaoAuditoria.AVANCO_ESTEIRA,
                TipoEntidadeAuditoria.ESTEIRA, "1", 1, "Ação 1",
                null, null, null, null, true, null
        ));

        auditoriaRepository.save(new AuditoriaGeral(
                "u2@empresa.com", "USER", TipoAcaoAuditoria.DEVOLUCAO_ESTEIRA,
                TipoEntidadeAuditoria.ESTEIRA, "1", 1, "Ação 2 falha",
                null, null, null, null, false, "Falha de validação"
        ));

        mockMvc.perform(get("/api/v1/auditoria/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistros").value(2))
                .andExpect(jsonPath("$.totalSucessos").value(1))
                .andExpect(jsonPath("$.totalFalhas").value(1))
                .andExpect(jsonPath("$.registrosPorTipoAcao.AVANCO_ESTEIRA").value(1))
                .andExpect(jsonPath("$.registrosPorTipoAcao.DEVOLUCAO_ESTEIRA").value(1));
    }

    @Test
    @DisplayName("BUG-111 / BACK-16: Deve exportar trilha de auditoria em CSV formatado com BOM UTF-8")
    void deveExportarAuditoriaEmCsvComBomUtf8() throws Exception {
        auditoriaRepository.save(new AuditoriaGeral(
                "auditor@empresa.com", "AUDITOR", TipoAcaoAuditoria.APROVACAO_PROGRAMA_PYTHON,
                TipoEntidadeAuditoria.PROGRAMA_PYTHON, "42", 1, "Aprovação de código fonte Python",
                null, null, "172.16.0.5", "Chrome", true, null
        ));

        MvcResult result = mockMvc.perform(get("/api/v1/auditoria/exportar")
                        .param("delimitador", ";"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("auditoria_geral.csv")))
                .andReturn();

        byte[] csvBytes = result.getResponse().getContentAsByteArray();
        assertThat(csvBytes.length).isGreaterThan(3);
        assertThat(csvBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(csvBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(csvBytes[2]).isEqualTo((byte) 0xBF);

        String csvText = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csvText).contains("Protocolo;DataHora;Usuario;PerfilUsuario;TipoAcao;TipoEntidade");
        assertThat(csvText).contains("auditor@empresa.com");
        assertThat(csvText).contains("APROVACAO_PROGRAMA_PYTHON");
        assertThat(csvText).contains("PROGRAMA_PYTHON");
    }

    @Test
    @DisplayName("BUG-111 / BACK-16: Deve disparar registro de auditoria automaticamente ao aprovar código Python e ao tramitar na esteira")
    void deveRegistrarAuditoriaAutomaticamenteAoAprovarCodigoPythonETramitarEsteira() throws Exception {
        // 1. Criar dados mestres para a campanha
        Brand brand = brandRepository.findByCode(30).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(30);
            b.setDescription("MARCA AUDIT");
            return brandRepository.save(b);
        });

        Store store = storeRepository.findByCode(90).orElseGet(() -> {
            Store s = new Store();
            s.setCode(90);
            s.setDescription("LOJA AUDIT");
            return storeRepository.save(s);
        });

        Position position = positionRepository.findByCode(300).orElseGet(() -> {
            Position p = new Position();
            p.setCode(300);
            p.setDescription("VENDEDOR AUDIT");
            return positionRepository.save(p);
        });

        registrationRepository.findByRegistration("MATRIC-AUD-01").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-AUD-01");
            r.setPosition(position);
            r.setStore(store);
            r.setAdmissDate(LocalDate.of(2022, 1, 1));
            return registrationRepository.save(r);
        });

        LocalDate mesRef = LocalDate.of(2026, 11, 1);
        baseComissRepository.findByBrandIdAndPositionIdAndReferenceMonth(brand.getId(), position.getId(), mesRef)
                .orElseGet(() -> {
                    BaseComiss bc = new BaseComiss(brand, position, new BigDecimal("0.0500"));
                    bc.setReferenceMonth(mesRef);
                    return baseComissRepository.save(bc);
                });

        // 2. Criar campanha
        RegraItemRequest regra = new RegraItemRequest(
                "bloco-1",
                "Comissão de 6% no ecommerce",
                "Regra E-commerce 6%",
                "ECOMMERCE",
                30, "MARCA AUDIT", null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA,
                null, null, null, null,
                BigDecimal.valueOf(0.06),
                "6% sobre vendas",
                "taxa = 0.06",
                null,
                true,
                null,
                null,
                null
        );

        CampanhaRequest campReq = new CampanhaRequest(
                "Campanha Teste Auditoria Automatica",
                "Descrição campanha audit",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                EstadoCampanha.DRAFT,
                List.of(regra),
                null
        );

        MvcResult campResult = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(campReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode campJson = objectMapper.readTree(campResult.getResponse().getContentAsString());
        Long campanhaId = campJson.get("id").asLong();

        // 3. Avançar esteira para etapa 2
        AvancarEsteiraRequest avancarReq = new AvancarEsteiraRequest(2, "analista.esteira");
        mockMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(avancarReq)))
                .andExpect(status().isOk());

        // 4. Homologar código Python
        MvcResult progResult = mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId))
                .andExpect(status().isOk())
                .andReturn();
        String checksum = objectMapper.readTree(progResult.getResponse().getContentAsString()).get("checksumSha256").asText();

        AprovarProgramaPythonRequest aprovReq = new AprovarProgramaPythonRequest("revisor.tecnico", checksum, "Homologação auditada");
        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aprovReq)))
                .andExpect(status().isOk());

        // 5. Verificar que os registros de auditoria foram automaticamente gerados
        List<AuditoriaGeral> auditEsteira = auditoriaRepository.findAllByTipoEntidadeAndEntidadeIdOrderByDataHoraDesc(
                TipoEntidadeAuditoria.ESTEIRA, String.valueOf(campanhaId));
        assertThat(auditEsteira).isNotEmpty();
        assertThat(auditEsteira.get(0).getTipoAcao()).isEqualTo(TipoAcaoAuditoria.AVANCO_ESTEIRA);
        assertThat(auditEsteira.get(0).getUsuario()).isEqualTo("analista.esteira");

        List<AuditoriaGeral> auditPython = auditoriaRepository.findAllByTipoEntidadeAndEntidadeIdOrderByDataHoraDesc(
                TipoEntidadeAuditoria.PROGRAMA_PYTHON, String.valueOf(campanhaId));
        assertThat(auditPython).isNotEmpty();
        assertThat(auditPython.get(0).getTipoAcao()).isEqualTo(TipoAcaoAuditoria.APROVACAO_PROGRAMA_PYTHON);
        assertThat(auditPython.get(0).getUsuario()).isEqualTo("revisor.tecnico");
    }
}

