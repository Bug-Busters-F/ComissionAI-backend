package com.bugbusters.backend.controller;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.preparacao.EditarLinhaPreparacaoRequestDTO;
import com.bugbusters.backend.dto.preparacao.EfetivarLotePreparacaoRequestDTO;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.support.CampaignDataCleanup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class CampanhaPreparacaoBaseControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private LotePreparacaoRepository lotePreparacaoRepository;

    @Autowired
    private LinhaPreparacaoRepository linhaPreparacaoRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private Campanha campanha;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        CampaignDataCleanup.clear(jdbcTemplate);

        jdbcTemplate.update("DELETE FROM tb_resultado_calculo");
        jdbcTemplate.update("DELETE FROM tb_sales");
        jdbcTemplate.update("DELETE FROM tb_registration");
        jdbcTemplate.update("DELETE FROM tb_inconsistencia_importacao");
        jdbcTemplate.update("DELETE FROM tb_envio_arquivo");

        brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("Marca Alpha");
            return brandRepository.save(b);
        });

        storeRepository.findByCode(20).orElseGet(() -> {
            Store s = new Store();
            s.setCode(20);
            s.setDescription("Loja Centro");
            return storeRepository.save(s);
        });

        positionRepository.findByCode(30).orElseGet(() -> {
            Position p = new Position();
            p.setCode(30);
            p.setDescription("Consultor");
            return positionRepository.save(p);
        });

        Campanha c = new Campanha("Campanha Controller Preparação", "Testes de integração REST", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30));
        campanha = campanhaRepository.save(c);
    }

    @Test
    @DisplayName("Fluxo completo de preparação: upload, listagem, consulta de linhas, edição, download e efetivação")
    void fluxoCompletoPreparacao() throws Exception {
        // 1. Upload de planilha com 1 linha inválida (sem cargo)
        MockMultipartFile file = criarExcelHr(new Object[][] {
                {"2026-05-01", 10, "Marca Alpha", 20, "Loja Centro", "M800", "2025-01-01", null, null, null}
        });

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/campanhas/{campanhaId}/preparacao-bases/upload", campanha.getId())
                        .file(file)
                        .param("tipoBase", "HR")
                        .param("competencia", "2026-05")
                        .param("origem", "MANUAL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EM_PREPARACAO"))
                .andExpect(jsonPath("$.aptaParaEfetivacao").value(false))
                .andExpect(jsonPath("$.contagens.totalLinhas").value(1))
                .andExpect(jsonPath("$.contagens.linhasInvalidas").value(1))
                .andReturn();

        JsonNode loteJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        UUID loteId = UUID.fromString(loteJson.get("id").asText());

        // 2. Listagem de lotes da campanha
        mockMvc.perform(get("/api/v1/campanhas/{campanhaId}/preparacao-bases", campanha.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(loteId.toString()));

        // 3. Consulta de linhas do lote
        MvcResult linhasResult = mockMvc.perform(get("/api/v1/campanhas/{campanhaId}/preparacao-bases/{preparacaoId}/linhas", campanha.getId(), loteId)
                        .param("valida", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].numeroLinha").value(2))
                .andExpect(jsonPath("$.content[0].erros", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();

        JsonNode linhasJson = objectMapper.readTree(linhasResult.getResponse().getContentAsString());
        int numeroLinha = linhasJson.get("content").get(0).get("numeroLinha").asInt();
        Long versaoLinha = linhasJson.get("content").get(0).get("versao").asLong();

        // 4. Edição da linha para corrigir o cargo
        EditarLinhaPreparacaoRequestDTO editRequest = new EditarLinhaPreparacaoRequestDTO(
                versaoLinha,
                "Correção via API",
                30,
                "Consultor"
        );

        mockMvc.perform(patch("/api/v1/campanhas/{campanhaId}/preparacao-bases/{preparacaoId}/linhas/{numeroLinha}", campanha.getId(), loteId, numeroLinha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusLinha").value("VALIDA"))
                .andExpect(jsonPath("$.erros").isEmpty());

        // 5. Download do arquivo original
        mockMvc.perform(get("/api/v1/campanhas/{campanhaId}/preparacao-bases/{preparacaoId}/arquivo-original", campanha.getId(), loteId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"base_rh.xlsx\"")));

        // 6. Efetivação do lote preparado
        EfetivarLotePreparacaoRequestDTO efetivarReq = new EfetivarLotePreparacaoRequestDTO(
                "admin",
                "Efetivação de teste via controller"
        );

        mockMvc.perform(post("/api/v1/campanhas/{campanhaId}/preparacao-bases/{preparacaoId}/efetivar", campanha.getId(), loteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(efetivarReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFETIVADO"))
                .andExpect(jsonPath("$.registrosInseridos").value(1))
                .andExpect(jsonPath("$.reportIdGerado").isNumber());

        // 7. Confirmação de persistência na base geral e anexo na campanha
        assertThat(registrationRepository.findByRegistration("M800")).isPresent();
        assertThat(campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(campanha.getId())).hasSize(1);
    }

    private MockMultipartFile criarExcelHr(Object[][] rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Cabeçalho");
            for (int i = 0; i < rows.length; i++) {
                var r = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    Object val = rows[i][j];
                    if (val instanceof Number n) {
                        r.createCell(j).setCellValue(n.doubleValue());
                    } else if (val != null) {
                        r.createCell(j).setCellValue(val.toString());
                    }
                }
            }
            workbook.write(output);
            return new MockMultipartFile("file", "base_rh.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
