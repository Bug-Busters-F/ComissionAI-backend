package com.bugbusters.backend.controller;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.fechamento.FechamentoRequestDTO;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.CampanhaVersao;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.StatusFechamento;
import com.bugbusters.backend.model.StatusVersaoCampanha;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.CampanhaVersaoRepository;
import com.bugbusters.backend.repository.FechamentoCompetenciaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
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

@SpringBootTest(properties = {"ai.service.url=http://localhost:8000", "spring.datasource.url=jdbc:h2:mem:fechamento_final;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@ActiveProfiles("test")
class FechamentoApuracaoFinalIntegrationTest {

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
    private com.bugbusters.backend.basecomiss.BaseComissRepository baseComissRepository;

    @Autowired
    private ResultadoCalculoRepository resultadoCalculoRepository;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired private org.springframework.jdbc.core.JdbcTemplate cleanupJdbc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        cleanupJdbc.update("DELETE FROM tb_log_calculo_imutavel");
        resultadoCalculoRepository.deleteAll();
        fechamentoCompetenciaRepository.deleteAll();
        com.bugbusters.backend.support.CampaignDataCleanup.clear(cleanupJdbc);
        saleRepository.deleteAll();
        baseComissRepository.deleteAll();
    }

    @Test
    @DisplayName("Cenário Completo BACK-11: Apuração com versão vigente retroativa, fechamento final e bloqueio de mutações")
    void deveExecutarFechamentoComVersaoVigenteEBloquearMutacoes() throws Exception {
        String competencia = "2026-10";
        LocalDate inicioMes = LocalDate.of(2026, 10, 1);
        LocalDate fimMes = LocalDate.of(2026, 10, 31);

        // 1. Dados mestres
        Brand brand = new Brand();
        brand.setCode(101);
        brand.setDescription("Marca Premium");
        brand = brandRepository.save(brand);

        Position position = new Position();
        position.setCode(201);
        position.setDescription("Vendedor");
        position = positionRepository.save(position);

        Store store = new Store();
        store.setCode(301);
        store.setDescription("Loja Centro");
        store = storeRepository.save(store);

        Registration reg = new Registration();
        reg.setRegistration("MAT-999");
        reg.setPosition(position);
        reg.setStore(store);
        reg.setAdmissDate(LocalDate.of(2025, 1, 1));
        reg = registrationRepository.save(reg);

        com.bugbusters.backend.basecomiss.BaseComiss baseComiss = new com.bugbusters.backend.basecomiss.BaseComiss(brand, position, new BigDecimal("0.0500"));
        baseComiss.setReferenceMonth(LocalDate.of(2026, 10, 1));
        baseComissRepository.save(baseComiss);

        // 2. Venda antiga ocorrida no início do mês (2026-10-05) antes da versão 2
        Sale sale1 = new Sale();
        sale1.setSaleDate(LocalDate.of(2026, 10, 5));
        sale1.setValue(new BigDecimal("2000.00"));
        sale1.setRegistration(reg);
        sale1.setBrand(brand);
        sale1.setStore(store);
        sale1.setSaleChannel("PADRAO");
        sale1 = saleRepository.save(sale1);

        // Venda recente ocorrida no final do mês (2026-10-25)
        Sale sale2 = new Sale();
        sale2.setSaleDate(LocalDate.of(2026, 10, 25));
        sale2.setValue(new BigDecimal("1000.00"));
        sale2.setRegistration(reg);
        sale2.setBrand(brand);
        sale2.setStore(store);
        sale2.setSaleChannel("PADRAO");
        sale2 = saleRepository.save(sale2);

        // 3. Campanha com versão vigente número 2 cobrindo todo o mês de Outubro
        Campanha campanha = new Campanha();
        campanha.setTitulo("Campanha Outubro Ouro");
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
        versao2.setTitulo("Campanha Outubro Ouro - v2");
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
        regraVersao2.setCodMarca(brand.getCode());
        regraVersao2.setCodCargo(position.getCode());
        regraRepository.save(regraVersao2);

        // 4. Executar o Fechamento Final via POST /api/v1/comissoes/fechamento
        FechamentoRequestDTO requestFechamento = new FechamentoRequestDTO(
                competencia,
                "controladoria@empresa.com",
                "Fechamento contábil oficial de Outubro/2026"
        );

        mockMvc.perform(post("/api/v1/comissoes/fechamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestFechamento)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.competencia").value("2026-10"))
                .andExpect(jsonPath("$.totalVendasProcessadas").value(2))
                .andExpect(jsonPath("$.totalCalculados").value(2))
                .andExpect(jsonPath("$.totalImpedimentos").value(0))
                .andExpect(jsonPath("$.valorTotalVendas").value(3000.00))
                .andExpect(jsonPath("$.valorTotalComissao").value(360.00))
                .andExpect(jsonPath("$.executadoPor").value("controladoria@empresa.com"))
                .andExpect(jsonPath("$.protocolo").isNotEmpty())
                .andExpect(jsonPath("$.resultados", hasSize(2)))
                // Valida que a versão vigente mais recente (versão 2) foi aplicada a TODAS as vendas do período
                .andExpect(jsonPath("$.resultados[0].versaoCampanhaNumero").value(2))
                .andExpect(jsonPath("$.resultados[1].versaoCampanhaNumero").value(2));

        // 5. Consultar status da competência -> deve estar CONCLUIDO e bloqueada = true
        mockMvc.perform(get("/api/v1/comissoes/fechamento/2026-10/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia").value("2026-10"))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.bloqueada").value(true))
                .andExpect(jsonPath("$.fechamentoId").isNotEmpty());

        // 6. Tentar executar fechamento novamente na mesma competência -> deve ser rejeitado (400)
        mockMvc.perform(post("/api/v1/comissoes/fechamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestFechamento)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("já possui fechamento final concluído")));

        // 7. Tentar criar nova campanha em período que intercepta a competência fechada -> deve ser rejeitada (400)
        RegraItemRequest novaRegra = new RegraItemRequest(
                "b1", "10%", "Nova Regra", null, null, null, null, null, null, null,
                null, null, null, null, TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.1000"),
                null, null, null, new BigDecimal("0.1000"), null, null, List.of(), true, StatusRegra.DRAFT
        );
        CampanhaRequest requestCriarCampanha = new CampanhaRequest(
                "Campanha Conflitante", "Desc",
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 20),
                EstadoCampanha.DRAFT, List.of(novaRegra)
        );

        mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestCriarCampanha)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("fechamento final concluído")));

        // 8. Tentar atualizar campanha vinculada à competência fechada -> deve ser rejeitada (400)
        mockMvc.perform(put("/api/v1/campanhas/" + campanha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestCriarCampanha)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("fechamento final concluído")));

        // 9. Tentar remover campanha vinculada à competência fechada -> deve ser rejeitada (400)
        mockMvc.perform(delete("/api/v1/campanhas/" + campanha.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("fechamento final concluído")));

        // 10. Listar fechamentos -> deve retornar a lista com o registro de fechamento
        mockMvc.perform(get("/api/v1/comissoes/fechamento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].competencia").value("2026-10"))
                .andExpect(jsonPath("$.content[0].status").value("CONCLUIDO"));
    }
}
