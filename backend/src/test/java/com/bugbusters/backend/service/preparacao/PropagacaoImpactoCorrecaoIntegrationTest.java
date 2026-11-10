package com.bugbusters.backend.service.preparacao;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.AprovacaoCodigoCampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.preparacao.EfetivarLotePreparacaoRequestDTO;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.simulacao.AceiteSimulacaoRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosResponse;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.CampanhaService;
import com.bugbusters.backend.service.SimulacaoCenariosService;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.bugbusters.backend.support.CampaignDataCleanup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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
class PropagacaoImpactoCorrecaoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private CampanhaService campanhaService;

    @Autowired
    private SimulacaoCenariosService simulacaoService;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private SimulacaoRepository simulacaoRepository;

    @Autowired
    private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private SnapshotResultadoCalculoRepository snapshotResultadoCalculoRepository;

    @Autowired
    private PropagacaoImpactoBaseRepository propagacaoImpactoBaseRepository;

    @Autowired
    private LotePreparacaoRepository lotePreparacaoRepository;

    @Autowired
    private LinhaPreparacaoRepository linhaPreparacaoRepository;

    @Autowired
    private ArquivoImportacaoRepository arquivoImportacaoRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private Campanha campanha;
    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;
    private Sale sale;
    private Regra regra;
    private Simulacao simulacao;
    private ArquivoImportacao arquivoImportacao;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        CampaignDataCleanup.clear(jdbcTemplate);

        jdbcTemplate.update("DELETE FROM tb_snapshot_resultado_calculo");
        jdbcTemplate.update("DELETE FROM tb_propagacao_impacto_base");
        jdbcTemplate.update("DELETE FROM tb_resultado_calculo");
        jdbcTemplate.update("DELETE FROM tb_sales");
        jdbcTemplate.update("DELETE FROM tb_registration");
        jdbcTemplate.update("DELETE FROM tb_inconsistencia_importacao");
        jdbcTemplate.update("DELETE FROM tb_envio_arquivo");
        jdbcTemplate.update("DELETE FROM tb_arquivo_importacao");

        brand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("Marca Alpha");
            return brandRepository.save(b);
        });

        store = storeRepository.findByCode(20).orElseGet(() -> {
            Store s = new Store();
            s.setCode(20);
            s.setDescription("Loja Matriz");
            return storeRepository.save(s);
        });

        position = positionRepository.findByCode(30).orElseGet(() -> {
            Position p = new Position();
            p.setCode(30);
            p.setDescription("Vendedor");
            return positionRepository.save(p);
        });

        registration = new Registration();
        registration.setRegistration("MAT-100");
        registration.setStore(store);
        registration.setPosition(position);
        registration.setAdmissDate(LocalDate.of(2026, 1, 1));
        registration = registrationRepository.save(registration);

        if (baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(10, 30).isEmpty()) {
            BaseComiss bc = new BaseComiss(brand, position, new BigDecimal("0.1000"));
            baseComissRepository.save(bc);
        }

        sale = new Sale();
        sale.setId(UUID.randomUUID());
        sale.setRegistration(registration);
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setSaleDate(LocalDate.of(2026, 9, 15));
        sale.setValue(new BigDecimal("1000.00"));
        sale = saleRepository.save(sale);

        RegraItemRequest regraDTO = new RegraItemRequest(
                "bloco-1", "Texto origem", "Regra Campanha BACK-6", null,
                10, null, 20, 30, "MAT-100", null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.1500"),
                null, null, List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest req = new CampanhaRequest(
                "Campanha Impacto BACK-6", "Campanha de testes de impacto",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                EstadoCampanha.DRAFT, List.of(regraDTO),
                new BigDecimal("500000.00"), null, true, "analista", null
        );

        var respCamp = campanhaService.criarCampanha(req);
        campanha = campanhaRepository.findById(respCamp.id()).orElseThrow();
        regra = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId()).get(0);

        SimulacaoCenariosRequest simReq = new SimulacaoCenariosRequest(
                "2026-09", campanha.getId(), campanha.getDataInicio(), campanha.getDataFim(),
                null, 50, 0, false, false
        );
        SimulacaoCenariosResponse simResp = simulacaoService.simularEPersistir(simReq);
        simulacao = simulacaoRepository.findById(simResp.id()).orElseThrow();
        simulacaoService.aceitarSimulacao(simResp.id(), new AceiteSimulacaoRequest("DIRETOR_COMERCIAL", "Aprovado em comite"));

        campanhaService.ativarCampanha(campanha.getId());
        campanha = campanhaRepository.findById(campanha.getId()).orElseThrow();
        regra = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId()).get(0);

        arquivoImportacao = new ArquivoImportacao("hash-fake-123", "vendas.csv", "text/csv", 100L, "conteudo".getBytes(), "TESTE");
        arquivoImportacao = arquivoImportacaoRepository.save(arquivoImportacao);
    }

    @Test
    @DisplayName("Deve gerar prévia de impacto detectando campanhas, simulações aceitas e cálculos dependentes")
    void deveGerarPreviaDeImpactoCorretamente() throws Exception {
        ResultadoCalculo resultado = new ResultadoCalculo(
                UUID.randomUUID(),
                sale.getId(),
                "MAT-100",
                brand.getCode(),
                store.getCode(),
                position.getCode(),
                regra.getId(),
                sale.getSaleDate(),
                sale.getValue(),
                regra.getTaxa(),
                new BigDecimal("150.00"),
                "COMPETENCIA"
        );
        resultadoCalculoRepository.save(resultado);

        LotePreparacao lote = new LotePreparacao(
                campanha,
                "SALES",
                "2026-09",
                "vendas_corrigidas.csv",
                "hash-lote-1",
                arquivoImportacao,
                "TESTE"
        );
        lote.setTotalLinhas(1);
        lote.setLinhasValidas(1);
        lote = lotePreparacaoRepository.save(lote);

        LinhaPreparacao linha = new LinhaPreparacao(lote, 1, "{\"matricula\":\"MAT-100\"}");
        linha.setRegistroAlvoId(sale.getId());
        linha.setMatricula("MAT-100");
        linha.setDataReferencia(LocalDate.of(2026, 9, 15));
        linha.setValorVenda(new BigDecimal("1200.00"));
        linha.setCodMarca(10);
        linha.setCodLoja(20);
        linha.setStatusLinha("VALIDA");
        linha.setErrosValidacaoJson("[]");
        linhaPreparacaoRepository.save(linha);

        mockMvc.perform(get("/api/v1/campanhas/{campanhaId}/preparacao-bases/{preparacaoId}/previa-impacto",
                        campanha.getId(), lote.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigeConfirmacaoExplicita").value(true))
                .andExpect(jsonPath("$.campanhasAfetadas", hasSize(1)))
                .andExpect(jsonPath("$.campanhasAfetadas[0].campanhaId").value(campanha.getId()))
                .andExpect(jsonPath("$.simulacoesAfetadas", hasSize(1)))
                .andExpect(jsonPath("$.simulacoesAfetadas[0].simulacaoId").value(simulacao.getId().toString()))
                .andExpect(jsonPath("$.calculosAfetados", hasSize(1)))
                .andExpect(jsonPath("$.calculosAfetados[0].competencia").value("2026-09"))
                .andExpect(jsonPath("$.calculosAfetados[0].qtdResultadosEmitidosAfetados").value(1));
    }

    @Test
    @DisplayName("Deve rejeitar efetivação com 409 se houver impactos e confirmação não for informada")
    void deveRejeitarEfetivacaoCom409QuandoNaoConfirmarImpacto() throws Exception {
        ResultadoCalculo resultado = new ResultadoCalculo(
                UUID.randomUUID(),
                sale.getId(),
                "MAT-100",
                brand.getCode(),
                store.getCode(),
                position.getCode(),
                regra.getId(),
                sale.getSaleDate(),
                sale.getValue(),
                regra.getTaxa(),
                new BigDecimal("150.00"),
                "COMPETENCIA"
        );
        resultadoCalculoRepository.save(resultado);

        LotePreparacao lote = new LotePreparacao(
                campanha,
                "SALES",
                "2026-09",
                "vendas_corrigidas.csv",
                "hash-lote-2",
                arquivoImportacao,
                "TESTE"
        );
        lote.setTotalLinhas(1);
        lote.setLinhasValidas(1);
        lote = lotePreparacaoRepository.save(lote);

        LinhaPreparacao linha = new LinhaPreparacao(lote, 1, "{\"matricula\":\"MAT-100\"}");
        linha.setRegistroAlvoId(sale.getId());
        linha.setMatricula("MAT-100");
        linha.setDataReferencia(LocalDate.of(2026, 9, 15));
        linha.setValorVenda(new BigDecimal("1200.00"));
        linha.setCodMarca(10);
        linha.setCodLoja(20);
        linha.setStatusLinha("VALIDA");
        linha.setErrosValidacaoJson("[]");
        linhaPreparacaoRepository.save(linha);

        EfetivarLotePreparacaoRequestDTO request = new EfetivarLotePreparacaoRequestDTO(
                "admin",
                "Tentativa sem confirmação de impacto",
                false
        );

        mockMvc.perform(post("/api/v1/campanhas/{campanhaId}/preparacao-bases/{preparacaoId}/efetivar",
                        campanha.getId(), lote.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.validacoes", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Deve efetivar com confirmação de impacto, invalidando simulações, preservando snapshot e marcando cálculo desatualizado")
    void deveEfetivarComConfirmacaoDeImpactoComSucesso() throws Exception {
        UUID protocoloOriginal = UUID.randomUUID();
        ResultadoCalculo resultado = new ResultadoCalculo(
                protocoloOriginal,
                sale.getId(),
                "MAT-100",
                brand.getCode(),
                store.getCode(),
                position.getCode(),
                regra.getId(),
                sale.getSaleDate(),
                sale.getValue(),
                regra.getTaxa(),
                new BigDecimal("150.00"),
                "COMPETENCIA"
        );
        resultado = resultadoCalculoRepository.save(resultado);

        LotePreparacao lote = new LotePreparacao(
                campanha,
                "SALES",
                "2026-09",
                "vendas_corrigidas.csv",
                "hash-lote-3",
                arquivoImportacao,
                "TESTE"
        );
        lote.setTotalLinhas(1);
        lote.setLinhasValidas(1);
        lote = lotePreparacaoRepository.save(lote);

        LinhaPreparacao linha = new LinhaPreparacao(lote, 1, "{\"matricula\":\"MAT-100\"}");
        linha.setRegistroAlvoId(sale.getId());
        linha.setMatricula("MAT-100");
        linha.setDataReferencia(LocalDate.of(2026, 9, 15));
        linha.setValorVenda(new BigDecimal("1200.00"));
        linha.setCodMarca(10);
        linha.setCodLoja(20);
        linha.setStatusLinha("VALIDA");
        linha.setErrosValidacaoJson("[]");
        linhaPreparacaoRepository.save(linha);

        EfetivarLotePreparacaoRequestDTO request = new EfetivarLotePreparacaoRequestDTO(
                "admin",
                "Correção auditada e autorizada pela diretoria",
                true
        );

        mockMvc.perform(post("/api/v1/campanhas/{campanhaId}/preparacao-bases/{preparacaoId}/efetivar",
                        campanha.getId(), lote.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFETIVADO"))
                .andExpect(jsonPath("$.resumoImpactos.qtdSimulacoesInvalidadas").value(1))
                .andExpect(jsonPath("$.resumoImpactos.qtdCalculosDesatualizados").value(1));

        // 1. Verificar simulação invalidada
        Simulacao simAtualizada = simulacaoRepository.findById(simulacao.getId()).orElseThrow();
        assertThat(simAtualizada.getStatusAceite()).isEqualTo(StatusAceiteSimulacao.INVALIDADA);
        assertThat(simAtualizada.getInvalidadoEm()).isNotNull();

        // 2. Verificar simulação desvinculada da campanha
        Campanha campanhaAtualizada = campanhaRepository.findById(campanha.getId()).orElseThrow();
        assertThat(campanhaAtualizada.getSimulacaoAceita()).isNull();

        // 3. Verificar cálculo marcado como desatualizado por correção
        ResultadoCalculo calcAtualizado = resultadoCalculoRepository.findById(resultado.getId()).orElseThrow();
        assertThat(calcAtualizado.isDesatualizadoPorCorrecao()).isTrue();
        assertThat(calcAtualizado.getDesatualizadoEm()).isNotNull();
        assertThat(calcAtualizado.getMotivoDesatualizacao()).contains("corrigida");
        assertThat(calcAtualizado.getLoteCorrecao()).isNotNull();

        // 4. Verificar snapshot de auditoria preservado
        var snapshots = snapshotResultadoCalculoRepository.findByIdVendaOrderByPreservadoEmDesc(sale.getId());
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getProtocoloCalculo()).isEqualTo(protocoloOriginal);
        assertThat(snapshots.get(0).getValorVenda()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(snapshots.get(0).getValorComissao()).isEqualByComparingTo(new BigDecimal("150.00"));

        // 5. Verificar log de auditoria de propagação
        var auditorias = propagacaoImpactoBaseRepository.findAll();
        assertThat(auditorias).hasSize(1);
        assertThat(auditorias.get(0).getQtdSimulacoesInvalidadas()).isEqualTo(1);
        assertThat(auditorias.get(0).getQtdCalculosDesatualizados()).isEqualTo(1);
        assertThat(auditorias.get(0).getMotivoConfirmacao()).isEqualTo("Correção auditada e autorizada pela diretoria");

        // 6. Consultar endpoint de snapshots da venda
        mockMvc.perform(get("/api/v1/comissoes/vendas/{idVenda}/snapshots", sale.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].protocoloCalculo").value(protocoloOriginal.toString()))
                .andExpect(jsonPath("$[0].valorVenda").value(1000.00));
    }

    @Test
    @DisplayName("Deve impedir cálculo avulso quando resultado prévio estiver desatualizado por correção de base")
    void deveImpedirCalculoAvulsoQuandoDesatualizadoPorCorrecao() throws Exception {
        ResultadoCalculo resultado = new ResultadoCalculo(
                UUID.randomUUID(),
                sale.getId(),
                "MAT-100",
                brand.getCode(),
                store.getCode(),
                position.getCode(),
                regra.getId(),
                sale.getSaleDate(),
                sale.getValue(),
                regra.getTaxa(),
                new BigDecimal("150.00"),
                "COMPETENCIA"
        );
        resultado.setDesatualizadoPorCorrecao(true);
        resultado.setDesatualizadoEm(OffsetDateTime.now());
        resultado.setMotivoDesatualizacao("Base de RH alterada");
        resultadoCalculoRepository.save(resultado);

        mockMvc.perform(post("/api/v1/comissoes/calcular/venda/{id}", sale.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("invalidado por correções na base")));
    }

    @Test
    @DisplayName("Deve permitir aprovação formal de código e invalidar aprovação quando regra for alterada")
    void deveAprovarCodigoEInvalidarQuandoRegraMudar() throws Exception {
        // 1. Aprovar código da campanha
        AprovacaoCodigoCampanhaRequest request = new AprovacaoCodigoCampanhaRequest(
                true,
                "Código validado conforme requisitos de negócio",
                "APROVADOR_SENIOR"
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/aprovacao-codigo", campanha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacaoCodigo").value("APROVADO"))
                .andExpect(jsonPath("$.codigoAprovadoPor").value("APROVADOR_SENIOR"))
                .andExpect(jsonPath("$.hashArtefatosCodigo").isNotEmpty());

        // 2. Consultar status
        mockMvc.perform(get("/api/v1/campanhas/{id}/aprovacao-codigo", campanha.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacaoCodigo").value("APROVADO"));

        // 3. Alterar taxa da regra
        regra.setTaxa(new BigDecimal("0.2000"));
        regraRepository.save(regra);

        // 4. Consultar status novamente: deve detectar que os artefatos foram alterados e invalidar aprovação
        mockMvc.perform(get("/api/v1/campanhas/{id}/aprovacao-codigo", campanha.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacaoCodigo").value("INVALIDADO"))
                .andExpect(jsonPath("$.motivoInvalidacaoCodigo").value(containsString("modificados após a aprovação formal")));
    }
}
