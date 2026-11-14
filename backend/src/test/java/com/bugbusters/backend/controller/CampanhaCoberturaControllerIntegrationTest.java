package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.cobertura.AnexarBaseCampanhaRequestDTO;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.dto.simulacao.AceiteSimulacaoRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.InconsistenciaImportacao;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaAnexoBaseRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.repository.InconsistenciaImportacaoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.CoberturaFechamentoService;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.support.CampaignDataCleanup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class CampanhaCoberturaControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;

    @Autowired
    private EnvioArquivoRepository envioArquivoRepository;

    @Autowired
    private InconsistenciaImportacaoRepository inconsistenciaRepository;

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
    private CoberturaFechamentoService coberturaFechamentoService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        limparDados();
    }

    @AfterEach
    void tearDown() {
        limparDados();
    }

    private void limparDados() {
        CampaignDataCleanup.clear(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM tb_inconsistencia_importacao");
        jdbcTemplate.update("DELETE FROM tb_sales");
        jdbcTemplate.update("DELETE FROM tb_basecomiss");
        jdbcTemplate.update("DELETE FROM tb_registration");
        jdbcTemplate.update("DELETE FROM tb_envio_arquivo");
    }

    @Test
    @DisplayName("validarParaFechamento sem transação ativa deve lançar IllegalTransactionStateException")
    void validarParaFechamentoSemTransacaoAtivaDeveFalhar() {
        Campanha campanha = new Campanha("Campanha Transacional", "Texto", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        campanha = campanhaRepository.save(campanha);

        Long campanhaId = campanha.getId();
        assertThatThrownBy(() -> coberturaFechamentoService.validarParaFechamento(campanhaId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("Fluxo end-to-end de cobertura, anexos, paginação de pendências e bloqueio de exclusão de lote")
    void fluxoEndToEndCoberturaEAnexos() throws Exception {
        // Preparar referências base
        Brand brand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("MARCA_COB_TEST");
            return brandRepository.save(b);
        });

        Store store = storeRepository.findByCode(75).orElseGet(() -> {
            Store s = new Store();
            s.setCode(75);
            s.setDescription("LOJA_COB_75");
            return storeRepository.save(s);
        });

        Position position = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR_COB_100");
            return positionRepository.save(p);
        });

        Registration registration = registrationRepository.findByRegistration("MAT-COB-001").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MAT-COB-001");
            r.setStore(store);
            r.setPosition(position);
            r.setAdmissDate(LocalDate.of(2026, 1, 1));
            return registrationRepository.save(r);
        });

        if (baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(10, 100).isEmpty()) {
            BaseComiss bc = new BaseComiss(brand, position, new BigDecimal("0.1000"));
            bc.setReferenceMonth(LocalDate.of(2026, 7, 1));
            baseComissRepository.save(bc);
        }

        Sale sale = new Sale();
        sale.setId(UUID.randomUUID());
        sale.setSaleDate(LocalDate.of(2026, 7, 10));
        sale.setValue(new BigDecimal("1000.00"));
        sale.setSaleChannel("ECOMMERCE");
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setIdLoteOrigem("lote-sales-int-01");
        saleRepository.save(sale);

        // 1. Criar e publicar campanha com vigência em 2026-07
        Long campanhaId = criarEPublicarCampanha("Campanha Fechamento Julho 2026", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // 2. Inserir lotes concluídos no banco
        EnvioArquivo loteHr = new EnvioArquivo();
        loteHr.setIdLote("lote-hr-int-01");
        loteHr.setReportId(9001L);
        loteHr.setNomeArquivo("rh_julho.xlsx");
        loteHr.setTipoBase("HR");
        loteHr.setStatus("CONCLUIDO");
        loteHr.setTotalLinhas(10);
        loteHr.setLinhasValidas(10);
        loteHr = envioArquivoRepository.save(loteHr);

        EnvioArquivo loteSales = new EnvioArquivo();
        loteSales.setIdLote("lote-sales-int-01");
        loteSales.setReportId(9002L);
        loteSales.setNomeArquivo("vendas_julho.xlsx");
        loteSales.setTipoBase("SALES");
        loteSales.setCompetencia("2026-07");
        loteSales.setStatus("CONCLUIDO");
        loteSales.setTotalLinhas(15);
        loteSales.setLinhasValidas(14);
        loteSales = envioArquivoRepository.save(loteSales);

        // Inconsistência no lote de vendas
        InconsistenciaImportacao inc = new InconsistenciaImportacao(
                loteSales, 5, "matricula", "Matrícula não cadastrada: 8888", "ADVERTENCIA"
        );
        inconsistenciaRepository.save(inc);

        EnvioArquivo loteComiss = new EnvioArquivo();
        loteComiss.setIdLote("lote-comiss-int-01");
        loteComiss.setReportId(9003L);
        loteComiss.setNomeArquivo("taxas_julho.xlsx");
        loteComiss.setTipoBase("COMISSIONS");
        loteComiss.setCompetencia("2026-07");
        loteComiss.setStatus("CONCLUIDO");
        loteComiss.setTotalLinhas(5);
        loteComiss.setLinhasValidas(5);
        loteComiss = envioArquivoRepository.save(loteComiss);

        // 3. Consultar diagnóstico inicial: deve apontar FALTA_ANEXO e indicar lotes disponíveis
        mockMvc.perform(get("/api/v1/campanhas/{id}/cobertura-fechamento", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campanhaId", is(campanhaId.intValue())))
                .andExpect(jsonPath("$.prontaParaFechamento", is(false)))
                .andExpect(jsonPath("$.totalCompetencias", is(1)))
                .andExpect(jsonPath("$.competencias[0].vendas.status", is("FALTA_ANEXO")))
                .andExpect(jsonPath("$.competencias[0].vendas.lotesDisponiveisNaoAnexados", hasSize(1)))
                .andExpect(jsonPath("$.competencias[0].vendas.lotesDisponiveisNaoAnexados[0].reportId", is(9002)));

        // 4. Anexar lote HR
        mockMvc.perform(post("/api/v1/campanhas/{id}/anexos-base", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnexarBaseCampanhaRequestDTO(9001L, "admin@teste.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId", is(9001)))
                .andExpect(jsonPath("$.tipoBase", is("HR")));

        // 5. Anexar lote SALES
        MvcResult resSales = mockMvc.perform(post("/api/v1/campanhas/{id}/anexos-base", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnexarBaseCampanhaRequestDTO(9002L, "admin@teste.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId", is(9002)))
                .andExpect(jsonPath("$.tipoBase", is("SALES")))
                .andReturn();

        JsonNode jsonSalesAnexo = objectMapper.readTree(resSales.getResponse().getContentAsString());
        UUID anexoSalesId = UUID.fromString(jsonSalesAnexo.get("id").asText());

        // 6. Anexar lote COMISSIONS
        mockMvc.perform(post("/api/v1/campanhas/{id}/anexos-base", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnexarBaseCampanhaRequestDTO(9003L, "admin@teste.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId", is(9003)))
                .andExpect(jsonPath("$.tipoBase", is("COMISSIONS")));

        // 7. Listar anexos ativos da campanha: deve conter 3 anexos
        mockMvc.perform(get("/api/v1/campanhas/{id}/anexos-base", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // 8. Consultar pendências paginadas da campanha
        mockMvc.perform(get("/api/v1/campanhas/{id}/cobertura-fechamento/pendencias", campanhaId)
                        .param("competencia", "2026-07")
                        .param("tipoBase", "SALES")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].campo", is("matricula")))
                .andExpect(jsonPath("$.content[0].motivo", containsString("8888")))
                .andExpect(jsonPath("$.totalElements", is(1)));

        // 9. Tentar excluir o lote 9002 via DELETE /api/v1/imports/{reportId} -> deve bloquear com 409 Conflict
        mockMvc.perform(delete("/api/v1/imports/{reportId}", 9002L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.validacoes[0].campo", is("LOTE_ANEXADO_CAMPANHA")))
                .andExpect(jsonPath("$.validacoes[0].motivo", containsString("Campanha Fechamento Julho 2026")));

        // 10. Desanexar o lote SALES via DELETE /api/v1/campanhas/{id}/anexos-base/{anexoId}
        mockMvc.perform(delete("/api/v1/campanhas/{id}/anexos-base/{anexoId}", campanhaId, anexoSalesId))
                .andExpect(status().isNoContent());

        // 11. Agora a exclusão do lote 9002 via DELETE /api/v1/imports/{reportId} deve ter sucesso (200 OK)
        mockMvc.perform(delete("/api/v1/imports/{reportId}", 9002L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("EXCLUIDO")));
    }

    private Long criarEPublicarCampanha(String titulo, LocalDate inicio, LocalDate fim) throws Exception {
        RegraItemRequest regra = new RegraItemRequest(
                "bloco-1", "Trecho 5% vendas", "Regra 5% E-commerce", "ECOMMERCE",
                10, null, 75, 100, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0500"),
                "Comissão de 5% sobre e-commerce",
                "taxa = 0.05",
                List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest criarReq = new CampanhaRequest(
                titulo, "Campanha teste de fechamento",
                inicio, fim,
                EstadoCampanha.DRAFT, List.of(regra),
                new BigDecimal("100000.00"), null, true, "analista", null
        );

        MvcResult postCampanhaResult = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criarReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        JsonNode campanhaJson = objectMapper.readTree(postCampanhaResult.getResponse().getContentAsString());
        long campanhaId = campanhaJson.get("id").asLong();

        // Simular proposta v1
        SimulacaoCenariosRequest simReqV1 = new SimulacaoCenariosRequest(
                "2026-07", campanhaId, inicio, fim,
                null, 50, 0, false, false
        );
        MvcResult simV1Result = mockMvc.perform(post("/api/v1/simulacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simReqV1)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID simV1Id = UUID.fromString(objectMapper.readTree(simV1Result.getResponse().getContentAsString()).get("id").asText());

        // Aceitar simulação v1
        AceiteSimulacaoRequest aceiteV1 = new AceiteSimulacaoRequest("gestor.v1@empresa.com", "Aprovado v1");
        mockMvc.perform(post("/api/v1/simulacoes/" + simV1Id + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceiteV1)))
                .andExpect(status().isOk());

        // Publicar versão 1
        mockMvc.perform(post("/api/v1/campanhas/" + campanhaId + "/ativar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.versaoVigente").value(1));

        return campanhaId;
    }
}
