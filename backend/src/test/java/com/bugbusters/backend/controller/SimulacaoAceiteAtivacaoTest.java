package com.bugbusters.backend.controller;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.dto.simulacao.AceiteSimulacaoRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosResponse;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.SimulacaoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.CampanhaService;
import com.bugbusters.backend.service.SimulacaoCenariosService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SimulacaoAceiteAtivacaoTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private SimulacaoCenariosService simulacaoService;

    @Autowired
    private CampanhaService campanhaService;

    @Autowired
    private CampanhaRepository campanhaRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private SimulacaoRepository simulacaoRepository;

    @Autowired
    private com.bugbusters.backend.repository.SimulacaoVendaRepository simulacaoVendaRepository;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        for (Campanha c : campanhaRepository.findAll()) {
            c.setSimulacaoAceita(null);
            campanhaRepository.saveAndFlush(c);
        }
        simulacaoVendaRepository.deleteAllInBatch();
        simulacaoRepository.deleteAllInBatch();
    }

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
    private com.bugbusters.backend.service.TaxaComissaoResolver taxaResolver;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private Campanha campanha;
    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        brand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("MARCA_TESTE_8");
            return brandRepository.save(b);
        });

        store = storeRepository.findByCode(75).orElseGet(() -> {
            Store s = new Store();
            s.setCode(75);
            s.setDescription("LOJA_75");
            return storeRepository.save(s);
        });

        position = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR_100");
            return positionRepository.save(p);
        });

        registration = registrationRepository.findByRegistration("MAT-B8-001").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MAT-B8-001");
            r.setStore(store);
            r.setPosition(position);
            r.setAdmissDate(LocalDate.of(2026, 1, 1));
            return registrationRepository.save(r);
        });

        if (baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(10, 100).isEmpty()) {
            BaseComiss bc = new BaseComiss(brand, position, new BigDecimal("0.1000"));
            baseComissRepository.save(bc);
        }

        // Criar venda válida na competência 2026-06
        Sale sale = new Sale();
        sale.setId(UUID.randomUUID());
        sale.setSaleDate(LocalDate.of(2026, 6, 10));
        sale.setValue(new BigDecimal("1000.00"));
        sale.setSaleChannel("ECOMMERCE");
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        saleRepository.save(sale);

        // Criar campanha de teste inicial em DRAFT
        RegraItemRequest regraDTO = new RegraItemRequest(
                "bloco-1", "Texto origem", "Regra 5% E-commerce", "ECOMMERCE",
                10, null, 75, 100, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0500"),
                null, null, List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest req = new CampanhaRequest(
                "Campanha Teste BACK-8", "Texto original",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                EstadoCampanha.DRAFT, List.of(regraDTO)
        );

        var resp = campanhaService.criarCampanha(req);
        campanha = campanhaRepository.findById(resp.id()).orElseThrow();
    }

    @Test
    @DisplayName("Registrar aceite de simulação concluída sem impedimentos com sucesso")
    void deveRegistrarAceiteDeSimulacaoSemImpedimentos() throws Exception {
        // 1. Simular e persistir
        SimulacaoCenariosRequest simReq = new SimulacaoCenariosRequest(
                "2026-06", campanha.getId(), campanha.getDataInicio(), campanha.getDataFim(),
                null, 50, 0, false, false
        );
        SimulacaoCenariosResponse simResp = simulacaoService.simularEPersistir(simReq);
        assertThat(simResp.id()).isNotNull();

        // 2. Registrar aceite
        AceiteSimulacaoRequest aceiteReq = new AceiteSimulacaoRequest("gestor@empresa.com", "Aprovado em comitê");
        mockMvc.perform(post("/api/v1/simulacoes/" + simResp.id() + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceiteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAceite").value("ACEITA"))
                .andExpect(jsonPath("$.aceitoPor").value("gestor@empresa.com"))
                .andExpect(jsonPath("$.motivoAceite").value("Aprovado em comitê"))
                .andExpect(jsonPath("$.hashProposta").isNotEmpty());

        // 3. Verificar persistência e vínculo com a campanha
        Campanha atualizada = campanhaRepository.findById(campanha.getId()).orElseThrow();
        assertThat(atualizada.getSimulacaoAceita()).isNotNull();
        assertThat(atualizada.getEstado()).isEqualTo(EstadoCampanha.DRAFT);
        assertThat(atualizada.getVersaoVigente()).isNull();
        assertThat(atualizada.getVersaoProposta().getNumero()).isEqualTo(1);
        assertThat(regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(atualizada.getVersaoProposta().getId()))
                .allMatch(r -> r.getStatus() == StatusRegra.DRAFT);
        assertThat(atualizada.getSimulacaoAceita().getId()).isEqualTo(simResp.id());
        assertThat(atualizada.getSimulacaoAceita().getStatusAceite()).isEqualTo(StatusAceiteSimulacao.ACEITA);
    }

    @Test
    @DisplayName("Rejeitar aceite de simulação que possui vendas com impedimentos")
    void deveRejeitarAceiteDeSimulacaoComImpedimentos() throws Exception {
        // Criar venda sem base cadastral para gerar impedimento na competência 2026-05
        Brand marcaSemBase = brandRepository.findByCode(99).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(99);
            b.setDescription("MARCA_SEM_BASE");
            return brandRepository.save(b);
        });

        Sale vendaImpedida = new Sale();
        vendaImpedida.setId(UUID.randomUUID());
        vendaImpedida.setSaleDate(LocalDate.of(2026, 5, 10));
        vendaImpedida.setValue(new BigDecimal("500.00"));
        vendaImpedida.setSaleChannel("ECOMMERCE");
        vendaImpedida.setBrand(marcaSemBase);
        vendaImpedida.setStore(store);
        vendaImpedida.setRegistration(registration);
        saleRepository.save(vendaImpedida);

        RegraItemRequest regraComImpedimento = new RegraItemRequest(
                "bloco-imp", "Texto", "Regra Impedida", "ECOMMERCE",
                99, null, 75, 100, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0500"),
                null, null, List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest reqCamp = new CampanhaRequest(
                "Campanha Com Impedimento", "Texto",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                EstadoCampanha.DRAFT, List.of(regraComImpedimento)
        );
        var respCamp = campanhaService.criarCampanha(reqCamp);

        SimulacaoCenariosRequest simReq = new SimulacaoCenariosRequest(
                "2026-05", respCamp.id(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                null, 50, 0, false, false
        );
        SimulacaoCenariosResponse simResp = simulacaoService.simularEPersistir(simReq);

        // Tentativa de aceitar simulação com impedimento deve retornar 400
        AceiteSimulacaoRequest aceiteReq = new AceiteSimulacaoRequest("gestor@empresa.com", "Tentativa indevida");
        mockMvc.perform(post("/api/v1/simulacoes/" + simResp.id() + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceiteReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("impedimentos cadastrais ou ausência de taxas base")));
    }

    @Test
    @DisplayName("Bloquear ativação de campanha que não possui nenhuma simulação aceita")
    void deveBloquearAtivacaoSemSimulacaoAceita() throws Exception {
        // Tentativa direta de ativar campanha DRAFT sem simulação via PATCH /estado
        mockMvc.perform(patch("/api/v1/campanhas/" + campanha.getId() + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"ATIVA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("não possui nenhuma simulação aceita")));

        // Tentativa direta de ativar via POST /ativar
        mockMvc.perform(post("/api/v1/campanhas/" + campanha.getId() + "/ativar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("não possui nenhuma simulação aceita")));
    }

    @Test
    @DisplayName("Bloquear ativação quando a proposta atual diverge da simulação aceita")
    void deveBloquearAtivacaoQuandoPropostaDivergeDaSimulacaoAceita() throws Exception {
        // 1. Simular e aceitar proposta com taxa 0.0500
        SimulacaoCenariosRequest simReq = new SimulacaoCenariosRequest(
                "2026-06", campanha.getId(), campanha.getDataInicio(), campanha.getDataFim(),
                null, 50, 0, false, false
        );
        SimulacaoCenariosResponse simResp = simulacaoService.simularEPersistir(simReq);
        simulacaoService.aceitarSimulacao(simResp.id(), new AceiteSimulacaoRequest("gestor@empresa.com", "Aceite"));

        // 2. Modificar a taxa da regra diretamente no banco simulando divergência maliciosa / descompasso
        List<Regra> regras = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId());
        regras.get(0).setTaxa(new BigDecimal("0.0800"));
        regraRepository.saveAll(regras);

        // 3. Tentativa de ativação deve ser detectada pela re-conferência e bloqueada (400)
        mockMvc.perform(post("/api/v1/campanhas/" + campanha.getId() + "/ativar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("A proposta atual da campanha diverge da simulação aceita")));
    }

    @Test
    @DisplayName("Invalidação automática do aceite quando campanha é editada via PUT")
    void deveInvalidarAceiteAoEditarCampanha() throws Exception {
        // 1. Simular e aceitar
        SimulacaoCenariosRequest simReq = new SimulacaoCenariosRequest(
                "2026-06", campanha.getId(), campanha.getDataInicio(), campanha.getDataFim(),
                null, 50, 0, false, false
        );
        SimulacaoCenariosResponse simResp = simulacaoService.simularEPersistir(simReq);
        simulacaoService.aceitarSimulacao(simResp.id(), new AceiteSimulacaoRequest("gestor@empresa.com", "Aceite"));

        // 2. Chamar PUT para atualizar regras da campanha
        RegraItemRequest novaRegra = new RegraItemRequest(
                "bloco-editado", "Texto editado", "Regra 6% Revisada", "ECOMMERCE",
                10, null, 75, 100, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0600"),
                null, null, List.of(), true, StatusRegra.DRAFT
        );
        CampanhaRequest editReq = new CampanhaRequest(
                "Campanha Editada", "Texto editado",
                campanha.getDataInicio(), campanha.getDataFim(),
                EstadoCampanha.DRAFT, List.of(novaRegra)
        );

        mockMvc.perform(put("/api/v1/campanhas/" + campanha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DRAFT"));

        // 3. Verificar que a simulação anterior foi marcada como INVALIDADA e o vínculo foi removido
        Simulacao simAnterior = simulacaoRepository.findById(simResp.id()).orElseThrow();
        assertThat(simAnterior.getStatusAceite()).isEqualTo(StatusAceiteSimulacao.INVALIDADA);
        assertThat(simAnterior.getMotivoInvalidacao()).contains("edição na proposta");

        Campanha campanhaEditada = campanhaRepository.findById(campanha.getId()).orElseThrow();
        assertThat(campanhaEditada.getSimulacaoAceita()).isNull();

        // 4. Tentativa de ativar sem nova simulação deve falhar
        mockMvc.perform(post("/api/v1/campanhas/" + campanha.getId() + "/ativar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("não possui nenhuma simulação aceita")));
    }

    @Test
    @DisplayName("Impedir contorno da validação por chamada direta à API (POST com estado ATIVA ou PUT com ATIVA)")
    void deveImpedirContornoDeValidacaoPorChamadaDireta() throws Exception {
        RegraItemRequest regra = new RegraItemRequest(
                "bloco-1", "Origem", "Regra", "ECOMMERCE",
                10, null, 75, 100, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null,
                null, null, null,
                new BigDecimal("0.0500"),
                null, null, List.of(), true, StatusRegra.ATIVA
        );

        // 1. Tentativa de criar diretamente com estado ATIVA
        CampanhaRequest postReq = new CampanhaRequest(
                "Campanha Direta Ativa", "Texto",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                EstadoCampanha.ATIVA, List.of(regra)
        );

        mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Não é permitido ativar uma campanha diretamente na criação")));

        // 2. Tentativa de atualizar via PUT forçando estado ATIVA
        CampanhaRequest putReq = new CampanhaRequest(
                "Campanha Put Direto", "Texto",
                campanha.getDataInicio(), campanha.getDataFim(),
                EstadoCampanha.ATIVA, List.of(regra)
        );

        mockMvc.perform(put("/api/v1/campanhas/" + campanha.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Não é permitido salvar alterações diretamente com estado ATIVA")));
    }

    @Test
    @DisplayName("Conferir correspondência de simulação via GET /conferencia-simulacao")
    void deveConferirCorrespondenciaDeSimulacao() throws Exception {
        // Antes de simular: aptaParaAtivacao deve ser false
        mockMvc.perform(get("/api/v1/campanhas/" + campanha.getId() + "/conferencia-simulacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aptaParaAtivacao").value(false))
                .andExpect(jsonPath("$.divergencias", hasItem(containsString("Nenhuma simulação aceita"))));

        // Simular e aceitar
        SimulacaoCenariosRequest simReq = new SimulacaoCenariosRequest(
                "2026-06", campanha.getId(), campanha.getDataInicio(), campanha.getDataFim(),
                null, 50, 0, false, false
        );
        SimulacaoCenariosResponse simResp = simulacaoService.simularEPersistir(simReq);
        simulacaoService.aceitarSimulacao(simResp.id(), new AceiteSimulacaoRequest("gestor@empresa.com", "Aceite"));

        // Após aceite: aptaParaAtivacao deve ser true
        mockMvc.perform(get("/api/v1/campanhas/" + campanha.getId() + "/conferencia-simulacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aptaParaAtivacao").value(true))
                .andExpect(jsonPath("$.statusAceite").value("ACEITA"))
                .andExpect(jsonPath("$.divergencias", empty()));
    }

    @Test
    @DisplayName("Fluxo completo homologado: DRAFT -> Simulação -> Aceite -> Ativação")
    void deveExecutarFluxoCompletoDeAtivacaoComSucesso() throws Exception {
        // 1. Simular e persistir
        SimulacaoCenariosRequest simReq = new SimulacaoCenariosRequest(
                "2026-06", campanha.getId(), campanha.getDataInicio(), campanha.getDataFim(),
                null, 50, 0, false, false
        );
        SimulacaoCenariosResponse simResp = simulacaoService.simularEPersistir(simReq);

        // 2. Registrar aceite
        mockMvc.perform(post("/api/v1/simulacoes/" + simResp.id() + "/aceite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AceiteSimulacaoRequest("comite@empresa.com", "Homologado"))))
                .andExpect(status().isOk());

        // 3. Ativar campanha
        mockMvc.perform(post("/api/v1/campanhas/" + campanha.getId() + "/ativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.regras[0].status").value("ATIVA"));

        Campanha ativada = campanhaRepository.findById(campanha.getId()).orElseThrow();
        assertThat(ativada.getEstado()).isEqualTo(EstadoCampanha.ATIVA);
        List<Regra> regrasAtivadas = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId());
        assertThat(regrasAtivadas.get(0).getStatus()).isEqualTo(StatusRegra.ATIVA);
    }

    @Test
    void deveRejeitarSimulacaoAnteriorAEdicaoMesmoSemAceitePrevio() {
        var sim = simularAtual();
        campanhaService.atualizarCampanha(campanha.getId(), propostaRevisada("0.0600"));
        assertThat(simulacaoRepository.findById(sim.id()).orElseThrow().getStatusAceite()).isEqualTo(StatusAceiteSimulacao.INVALIDADA);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> aceitar(sim.id()))
                .isInstanceOf(com.bugbusters.backend.exception.BusinessException.class);
        assertThat(campanha.getEstado()).isEqualTo(EstadoCampanha.DRAFT);
    }

    @Test
    void naoDeveConcederAceiteAutomaticoAoConsultarConferencia() {
        var sim = simularAtual();
        assertThat(campanhaService.conferirSimulacao(campanha.getId()).aptaParaAtivacao()).isFalse();
        assertThat(simulacaoRepository.findById(sim.id()).orElseThrow().getStatusAceite()).isEqualTo(StatusAceiteSimulacao.PENDENTE);
    }

    @Test
    void deveManterVersaoPublicadaAoEditarSimularEAceitarUmaRevisao() {
        var primeira = publicarInicial();
        var vigente = campanha.getVersaoVigente();
        var regrasVigentes = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(vigente.getId());
        var req = propostaRevisada("0.0600");
        req = new CampanhaRequest("Título revisado", "Texto revisado", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), EstadoCampanha.DRAFT, req.regras());
        var salva = campanhaService.atualizarCampanha(campanha.getId(), req);
        assertThat(salva.titulo()).isEqualTo("Título revisado");
        assertThat(salva.dataInicio()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(salva.versaoProposta()).isEqualTo(2);
        assertThat(campanha.getTitulo()).isEqualTo("Campanha Teste BACK-8");
        assertThat(campanha.getDataInicio()).isEqualTo(LocalDate.of(2026, 6, 1));
        var segunda = simularAtual();
        assertThat(segunda.tituloCampanha()).isEqualTo("Título revisado");
        assertThat(segunda.dataInicioCampanha()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(segunda.diasInclusivos()).isEqualTo(31);
        aceitar(segunda.id());
        assertThat(campanha.getVersaoVigente()).isSameAs(vigente);
        assertThat(regrasVigentes).allMatch(r -> r.getStatus() == StatusRegra.ATIVA && r.getTaxa().compareTo(new BigDecimal("0.0500")) == 0);
        assertThat(campanhaService.conferirSimulacao(campanha.getId()).aptaParaAtivacao()).isTrue();
        assertThat(simulacaoRepository.findById(primeira.id()).orElseThrow().getStatusAceite()).isEqualTo(StatusAceiteSimulacao.ACEITA);
    }

    @Test
    void devePublicarRevisaoSomenteNaAtivacaoFinalComVersaoEsperada() throws Exception {
        var primeira = publicarInicial();
        var anterior = campanha.getVersaoVigente();
        campanhaService.atualizarCampanha(campanha.getId(), propostaRevisada("0.0600"));
        var segunda = simularAtual();
        aceitar(segunda.id());
        assertThat(taxaResolver.resolverTaxa(vendaDaCampanha()).taxa()).isEqualByComparingTo("0.0500");
        mockMvc.perform(post("/api/v1/campanhas/" + campanha.getId() + "/ativar")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/campanhas/" + campanha.getId() + "/ativar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versaoEsperada\":99}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/campanhas/" + campanha.getId() + "/ativar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versaoEsperada\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versaoVigente").value(2))
                .andExpect(jsonPath("$.versaoProposta").doesNotExist());
        assertThat(regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(anterior.getId()))
                .allMatch(r -> r.getStatus() == StatusRegra.INATIVA);
        assertThat(regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getVersaoVigente().getId()))
                .allMatch(r -> r.getStatus() == StatusRegra.ATIVA && r.getTaxa().compareTo(new BigDecimal("0.0600")) == 0);
        assertThat(simulacaoRepository.findById(primeira.id()).orElseThrow().getStatusAceite()).isEqualTo(StatusAceiteSimulacao.ACEITA);
        assertThat(taxaResolver.resolverTaxa(vendaDaCampanha()).taxa()).isEqualByComparingTo("0.0600");
    }

    @Test
    void devePermitirOutraRevisaoAposCancelarPreservandoHistoricoEAceiteVigente() {
        var primeira = publicarInicial();
        campanhaService.atualizarCampanha(campanha.getId(), propostaRevisada("0.0600"));
        var cancelada = campanha.getVersaoProposta();
        var segunda = simularAtual();
        aceitar(segunda.id());
        campanhaService.cancelarProposta(campanha.getId());
        assertThat(cancelada.getStatus()).isEqualTo(StatusVersaoCampanha.CANCELADA);
        assertThat(campanha.getVersaoProposta()).isNull();
        assertThat(campanha.getVersaoVigente().getNumero()).isEqualTo(1);
        assertThat(campanha.getEstado()).isEqualTo(EstadoCampanha.ATIVA);
        assertThat(campanha.getSimulacaoAceita().getId()).isEqualTo(primeira.id());
        assertThat(simulacaoRepository.findById(segunda.id()).orElseThrow().getStatusAceite()).isEqualTo(StatusAceiteSimulacao.INVALIDADA);
        var nova = campanhaService.atualizarCampanha(campanha.getId(), propostaRevisada("0.0700"));
        assertThat(nova.versaoProposta()).isEqualTo(3);
        assertThat(cancelada.getStatus()).isEqualTo(StatusVersaoCampanha.CANCELADA);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> aceitar(segunda.id()))
                .isInstanceOf(com.bugbusters.backend.exception.BusinessException.class);
    }

    @Test
    void deveInativarRegrasVigentesSemAtivarOuAlterarAPropostaPendente() {
        publicarInicial();
        var vigente = campanha.getVersaoVigente();
        campanhaService.atualizarCampanha(campanha.getId(), propostaRevisada("0.0600"));
        var proposta = campanha.getVersaoProposta();
        campanhaService.alterarEstado(campanha.getId(), EstadoCampanha.INATIVA);
        assertThat(regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(vigente.getId()))
                .allMatch(r -> r.getStatus() == StatusRegra.INATIVA);
        assertThat(regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(proposta.getId()))
                .allMatch(r -> r.getStatus() == StatusRegra.DRAFT);
    }

    @Test
    void deveRejeitarSimulacaoAntigaDeOutraVersaoMesmoComCamposIguais() {
        var primeira = publicarInicial();
        campanhaService.atualizarCampanha(campanha.getId(), propostaRevisada("0.0500"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> aceitar(primeira.id()))
                .isInstanceOf(com.bugbusters.backend.exception.BusinessException.class);
        assertThat(campanha.getVersaoVigente().getNumero()).isEqualTo(1);
    }

    private SimulacaoCenariosResponse simularAtual() {
        // Omite datas para verificar a resolução da vigência da proposta, sem usar a versão vigente.
        return simulacaoService.simularEPersistir(new SimulacaoCenariosRequest(
                "2026-06", campanha.getId(), null, null, null, 50, 0, false, false));
    }

    private Sale vendaDaCampanha() {
        return saleRepository.findAll().stream()
                .filter(v -> v.getRegistration() != null && "MAT-B8-001".equals(v.getRegistration().getRegistration()))
                .findFirst().orElseThrow();
    }

    private void aceitar(UUID id) {
        simulacaoService.aceitarSimulacao(id, new AceiteSimulacaoRequest("gestor", "Conferido"));
    }

    private SimulacaoCenariosResponse publicarInicial() {
        var sim = simularAtual();
        aceitar(sim.id());
        campanhaService.ativarCampanha(campanha.getId());
        return sim;
    }

    private CampanhaRequest propostaRevisada(String taxa) {
        var regra = new RegraItemRequest("revisada", "Texto revisado", "Regra revisada", "ECOMMERCE",
                10, null, 75, 100, null, null, null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, null, null, null, null,
                new BigDecimal(taxa), null, null, List.of(), true, StatusRegra.DRAFT);
        return new CampanhaRequest("Campanha revisada", "Texto revisado", campanha.getDataInicio(),
                campanha.getDataFim(), EstadoCampanha.DRAFT, List.of(regra));
    }

}
