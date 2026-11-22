package com.bugbusters.backend.controller;

import com.bugbusters.backend.auth.AuthService;
import com.bugbusters.backend.auth.PapelUsuario;
import com.bugbusters.backend.auth.Usuario;
import com.bugbusters.backend.auth.UsuarioRepository;
import com.bugbusters.backend.auth.dto.LoginRequest;
import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.apuracao.CalculoDefinitivoCampanhaRequest;
import com.bugbusters.backend.dto.auditoria.RegistroAuditoriaDTO;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.esteira.AprovarGerenteRequest;
import com.bugbusters.backend.dto.esteira.AvancarEsteiraRequest;
import com.bugbusters.backend.dto.esteira.DevolverCampanhaRequest;
import com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.pendencia.AceitarPendenciaRequest;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.AtualizarProgramaPythonRequest;
import com.bugbusters.backend.dto.relatorio.RelatorioFechamentoConsolidadoDTO;
import com.bugbusters.backend.dto.simulacao.AceiteSimulacaoRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoPythonRequest;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.RelatorioFechamentoService;
import com.bugbusters.backend.service.client.AiServiceClient;
import com.bugbusters.backend.service.pendencia.AceitePendenciaProvider;
import com.bugbusters.backend.service.pendencia.CampanhaAceitePendenciaProvider;
import com.bugbusters.backend.service.pendencia.StatusAceitePendenciaDTO;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Pr34ReviewFixesIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy springSecurityFilterChain;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AuthService authService;

    @Autowired private BrandRepository brandRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private BaseComissRepository baseComissRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private EnvioArquivoRepository envioArquivoRepository;
    @Autowired private InconsistenciaImportacaoRepository inconsistenciaRepository;
    @Autowired private FechamentoCompetenciaRepository fechamentoRepository;
    @Autowired private ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired private AceitePendenciaProvider aceitePendenciaProvider;
    @Autowired private RelatorioFechamentoService relatorioFechamentoService;
    @Autowired private RestClient.Builder aiRestClientBuilder;
    @Autowired private AiServiceClient aiServiceClient;

    private MockMvc secureMvc;
    private PythonSandboxMockSupport sandboxMock;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;

    @BeforeEach
    void setUp() {
        secureMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
        sandboxMock = PythonSandboxMockSupport.instalar(aiRestClientBuilder, aiServiceClient)
                .registrarExecucaoPadrao();

        brand = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("MARCA PR34");
            return brandRepository.save(b);
        });

        store = storeRepository.findByCode(75).orElseGet(() -> {
            Store s = new Store();
            s.setCode(75);
            s.setDescription("LOJA PR34");
            return storeRepository.save(s);
        });

        position = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("CARGO PR34");
            return positionRepository.save(p);
        });

        registration = registrationRepository.findByRegistration("MATRIC-PR34").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-PR34");
            r.setPosition(position);
            r.setStore(store);
            r.setAdmissDate(LocalDate.of(2020, 1, 1));
            return registrationRepository.save(r);
        });

        LocalDate mesRef = LocalDate.of(2026, 7, 1);
        baseComissRepository.findByBrandIdAndPositionIdAndReferenceMonth(brand.getId(), position.getId(), mesRef)
                .orElseGet(() -> {
                    BaseComiss bc = new BaseComiss(brand, position, new BigDecimal("0.0300"));
                    bc.setReferenceMonth(mesRef);
                    return baseComissRepository.save(bc);
                });

        Sale sale = new Sale();
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setRegistration(registration);
        sale.setSaleChannel("ECOMMERCE");
        sale.setSaleDate(LocalDate.of(2026, 7, 15));
        sale.setValue(new BigDecimal("10000.00"));
        saleRepository.save(sale);
    }

    @AfterEach
    void tearDown() {
        if (sandboxMock != null) {
            sandboxMock.restaurar();
        }
    }

    private String loginComo(String email, PapelUsuario papel) {
        Usuario u = usuarios.findByEmailIgnoreCase(email).orElseGet(() -> {
            Usuario novo = new Usuario();
            novo.setNome("Teste " + papel.name());
            novo.setEmail(email);
            novo.setPapel(papel);
            novo.setSenhaHash(encoder.encode("Senha@123"));
            return usuarios.saveAndFlush(novo);
        });
        return authService.login(new LoginRequest(u.getEmail(), "Senha@123")).token();
    }

    private Long criarCampanhaComToken(String token) throws Exception {
        RegraItemRequest regra = new RegraItemRequest(
                "bloco-pr34",
                "Comissão de 5% ecommerce",
                "Regra PR34",
                "ECOMMERCE",
                10, "MARCA PR34", 75, 100, "CARGO PR34", null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA,
                null, null, null, null,
                new BigDecimal("0.0500"),
                "5% ecommerce",
                "taxa = 0.05",
                null,
                true,
                null,
                null,
                null
        );

        CampanhaRequest req = new CampanhaRequest(
                "Campanha Seguranca PR34",
                "Texto campanha PR34",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                EstadoCampanha.DRAFT,
                List.of(regra),
                null,
                new BigDecimal("50000.00"),
                null,
                true,
                "negocios@pr34.local",
                "Orçamento PR34"
        );

        MvcResult res = secureMvc.perform(post("/api/v1/campanhas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("Segurança PR34: Matriz de perfis, bloqueio anônimo (401) e identidade real via UsuarioAtualProvider")
    void deveRespeitarMatrizPermissoesEResolverUsuarioAutenticado() throws Exception {
        String tkNegocios = loginComo("negocios.pr34@teste.local", PapelUsuario.ANALISTA_NEGOCIOS);
        String tkCodigo = loginComo("codigo.pr34@teste.local", PapelUsuario.ANALISTA_CODIGO);
        String tkGerente = loginComo("gerente.pr34@teste.local", PapelUsuario.GERENTE_VENDAS);
        String tkAuditor = loginComo("auditor.pr34@teste.local", PapelUsuario.AUDITOR);

        // Anônimo bloqueado (401)
        secureMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isUnauthorized());

        Long campanhaId = criarCampanhaComToken(tkNegocios);

        // NEGOCIOS não pode aprovar programa Python, devolver, aprovar gerente, calcular definitivo nem ler auditoria (403)
        secureMvc.perform(get("/api/v1/auditoria").header("Authorization", "Bearer " + tkNegocios))
                .andExpect(status().isForbidden());
        secureMvc.perform(post("/api/v1/campanhas/{id}/esteira/aprovar", campanhaId)
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarGerenteRequest("spoof", "ok"))))
                .andExpect(status().isForbidden());
        secureMvc.perform(post("/api/v1/campanhas/{id}/esteira/devolver", campanhaId)
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DevolverCampanhaRequest(1, "motivo", "spoof"))))
                .andExpect(status().isForbidden());
        secureMvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtualizarProgramaPythonRequest("def x(): pass", "spoof", "ok"))))
                .andExpect(status().isForbidden());
        secureMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CalculoDefinitivoCampanhaRequest("2026-07", null, null, "spoof", "obs", false))))
                .andExpect(status().isForbidden());

        // AUDITOR lê auditoria e relatório, mas é bloqueado (403) em mutações
        secureMvc.perform(get("/api/v1/auditoria").header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk());
        secureMvc.perform(post("/api/v1/auditoria")
                        .header("Authorization", "Bearer " + tkAuditor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistroAuditoriaDTO(
                                "spoof", "AUDITOR", TipoAcaoAuditoria.AVANCO_ESTEIRA, TipoEntidadeAuditoria.ESTEIRA,
                                "1", 1, "desc", null, "{}", null, null, true, null))))
                .andExpect(status().isForbidden());
        secureMvc.perform(post("/api/v1/campanhas/{id}/esteira/aprovar", campanhaId)
                        .header("Authorization", "Bearer " + tkAuditor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarGerenteRequest("spoof", "ok"))))
                .andExpect(status().isForbidden());

        // NEGOCIOS avança 1 -> 2
        secureMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(2, "forjado@evil.com"))))
                .andExpect(status().isOk());

        // GERENTE não pode aprovar código Python (403)
        MvcResult progRes = secureMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .header("Authorization", "Bearer " + tkCodigo))
                .andExpect(status().isOk())
                .andReturn();
        String checksum = objectMapper.readTree(progRes.getResponse().getContentAsString()).get("checksumSha256").asText();

        secureMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .header("Authorization", "Bearer " + tkGerente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarProgramaPythonRequest("spoof", checksum, "ok"))))
                .andExpect(status().isForbidden());

        // CODIGO aprova programa Python -> aprovadoPor deve refletir o usuário autenticado, nunca o payload forjado
        secureMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .header("Authorization", "Bearer " + tkCodigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarProgramaPythonRequest("forjado@evil.com", checksum, "Homologado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprovadoPor").value("codigo.pr34@teste.local"));

        // CODIGO não pode aprovar gerencialmente (403)
        secureMvc.perform(post("/api/v1/campanhas/{id}/esteira/aprovar", campanhaId)
                        .header("Authorization", "Bearer " + tkCodigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarGerenteRequest("spoof", "ok"))))
                .andExpect(status().isForbidden());

        // NEGOCIOS simula e aceita simulação
        MvcResult simRes = secureMvc.perform(post("/api/v1/campanhas/{id}/simulacao-python", campanhaId)
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SimulacaoPythonRequest("2026-07", null, 50, 0, false, false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.executadoViaPython").value(true))
                .andReturn();
        UUID simId = UUID.fromString(objectMapper.readTree(simRes.getResponse().getContentAsString()).get("id").asText());

        secureMvc.perform(post("/api/v1/simulacoes/{id}/aceite", simId)
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AceiteSimulacaoRequest("forjado@evil.com", "Aceite"))))
                .andExpect(status().isOk());

        secureMvc.perform(post("/api/v1/campanhas/{id}/esteira/avancar", campanhaId)
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AvancarEsteiraRequest(4, "forjado"))))
                .andExpect(status().isOk());

        // GERENTE aprova gerencialmente -> gerenteAprovador deve ser gerente.pr34@teste.local
        secureMvc.perform(post("/api/v1/campanhas/{id}/esteira/aprovar", campanhaId)
                        .header("Authorization", "Bearer " + tkGerente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AprovarGerenteRequest("forjado@evil.com", "Parecer gerente"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoEsteira").value("APROVADA"))
                .andExpect(jsonPath("$.gerenteAprovador").value("gerente.pr34@teste.local"));

        // GERENTE executa cálculo definitivo e AUDITOR consulta o relatório
        secureMvc.perform(post("/api/v1/campanhas/{id}/calcular-definitivo", campanhaId)
                        .header("Authorization", "Bearer " + tkGerente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CalculoDefinitivoCampanhaRequest("2026-07", null, null, "forjado", "Liquidação", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executadoViaPython").value(true))
                .andExpect(jsonPath("$.executadoPor").value("gerente.pr34@teste.local"));

        secureMvc.perform(get("/api/v1/campanhas/{id}/apuracao-definitiva/relatorio", campanhaId)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Pendências PR34: Aceite não vaza para outra regra com mesmo código e conecta CampanhaAceitePendenciaProvider")
    void deveIsolarAceitePorIdentificadorEConectarAceitePendenciaProvider() throws Exception {
        assertThat(aceitePendenciaProvider).isInstanceOf(CampanhaAceitePendenciaProvider.class);

        String tkNegocios = loginComo("negocios.pend@teste.local", PapelUsuario.ANALISTA_NEGOCIOS);
        String tkGerente = loginComo("gerente.pend@teste.local", PapelUsuario.GERENTE_VENDAS);

        String campanhaJson = """
            {
              "titulo": "Campanha Duas Regras Pendencias",
              "textoOriginal": "Regra 1 e Regra 2 com mesmo código de pendência",
              "estado": "DRAFT",
              "dataInicio": "2026-07-01",
              "dataFim": "2026-07-31",
              "orcamento": 50000.00,
              "orcamentoConfirmado": true,
              "regras": [
                {
                  "blocoId": "regra-a",
                  "nome": "Regra A",
                  "canal": "ECOMMERCE",
                  "taxa": 0.05,
                  "valorAjuste": 0.05,
                  "tipoOperacao": "DEFINIR_TAXA",
                  "completa": false,
                  "rastreabilidade": {
                    "origensPorCampo": { "canal": "TEXTO" },
                    "camposManuais": [],
                    "publicoTodasVendas": false,
                    "artefatosAtualizados": true,
                    "referenciasConsultadas": [],
                    "pendencias": [
                      {
                        "id": "pend-a-999",
                        "campo": "filtros.codLoja",
                        "codigo": "LOJA_FORA_CATALOGO",
                        "mensagem": "Loja A fora do catálogo",
                        "severidade": "IMPEDIMENTO"
                      }
                    ],
                    "pendenciasCampanha": []
                  }
                },
                {
                  "blocoId": "regra-b",
                  "nome": "Regra B",
                  "canal": "LOJA_FISICA",
                  "taxa": 0.04,
                  "valorAjuste": 0.04,
                  "tipoOperacao": "DEFINIR_TAXA",
                  "completa": false,
                  "rastreabilidade": {
                    "origensPorCampo": { "canal": "TEXTO" },
                    "camposManuais": [],
                    "publicoTodasVendas": false,
                    "artefatosAtualizados": true,
                    "referenciasConsultadas": [],
                    "pendencias": [
                      {
                        "id": "pend-b-888",
                        "campo": "filtros.codLoja",
                        "codigo": "LOJA_FORA_CATALOGO",
                        "mensagem": "Loja B fora do catálogo",
                        "severidade": "IMPEDIMENTO"
                      }
                    ],
                    "pendenciasCampanha": []
                  }
                }
              ]
            }
            """;

        MvcResult createRes = secureMvc.perform(post("/api/v1/campanhas")
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campanhaJson))
                .andExpect(status().isCreated())
                .andReturn();
        Long campanhaId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // Aceitar apenas a pendência da Regra A ("pend-a-999")
        AceitarPendenciaRequest aceitaA = new AceitarPendenciaRequest(
                null, "pend-a-999", "LOJA_FORA_CATALOGO", "filtros.codLoja",
                "Loja A", SeveridadePendencia.IMPEDIMENTO, "gerente", "Justificativa exclusiva da Regra A"
        );
        secureMvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite", campanhaId)
                        .header("Authorization", "Bearer " + tkGerente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceitaA)))
                .andExpect(status().isCreated());

        // Verificar que Regra B continua pendente (totalPendentesImpeditivas == 1, aptaParaAvanco == false)
        secureMvc.perform(get("/api/v1/campanhas/{id}/pendencias", campanhaId)
                        .header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalImpedimentos").value(2))
                .andExpect(jsonPath("$.totalPendentesImpeditivas").value(1))
                .andExpect(jsonPath("$.aptaParaAvanco").value(false));

        // Conectar CampanhaAceitePendenciaProvider a inconsistências reais de importação
        EnvioArquivo envio = new EnvioArquivo();
        envio.setNomeArquivo("vendas-teste.csv");
        envio.setTipoBase("VENDAS");
        envio.setStatus("CONCLUIDO");
        envio.setTotalLinhas(2);
        envio.setLinhasValidas(0);
        envio.setRejeicaoIntegral(false);
        envio.setReportId(998877L);
        envio = envioArquivoRepository.save(envio);

        InconsistenciaImportacao incAlvo = inconsistenciaRepository.save(
                new InconsistenciaImportacao(envio, 10, "cod_loja", "Loja ausente linha 10", "CRITICA")
        );
        InconsistenciaImportacao incIrma = inconsistenciaRepository.save(
                new InconsistenciaImportacao(envio, 20, "cod_loja", "Loja ausente linha 20", "CRITICA")
        );

        AceitarPendenciaRequest aceitaInc = new AceitarPendenciaRequest(
                null,
                "lote:998877.linha:10",
                "INCONSISTENCIA_LOTE",
                "cod_loja",
                "Inconsistência linha 10",
                SeveridadePendencia.IMPEDIMENTO,
                "gerente",
                "Aceite específico da linha 10 via lote",
                envio.getId(),
                incAlvo.getId()
        );
        secureMvc.perform(post("/api/v1/campanhas/{id}/pendencias/aceite", campanhaId)
                        .header("Authorization", "Bearer " + tkGerente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aceitaInc)))
                .andExpect(status().isCreated());

        StatusAceitePendenciaDTO statusAlvo = aceitePendenciaProvider.consultarAceite(campanhaId, envio.getId(), incAlvo);
        StatusAceitePendenciaDTO statusIrma = aceitePendenciaProvider.consultarAceite(campanhaId, envio.getId(), incIrma);

        assertThat(statusAlvo.aceita()).isTrue();
        assertThat(statusAlvo.aceitaPor()).isEqualTo("gerente.pend@teste.local");
        assertThat(statusIrma.aceita()).isFalse();
    }

    @Test
    @DisplayName("Relatório PR34: Deve manter linhas distintas por loja/cargo para mesma matrícula preservando colaboradoresDistintos")
    void deveAgregarColaboradorPorMatriculaLojaCargoEMarca() {
        FechamentoCompetencia fech = new FechamentoCompetencia("2026-10", UUID.randomUUID(), "gerente.pr34", "Fechamento multi-loja");
        fech.setStatus(StatusFechamento.CONCLUIDO);
        fech.setConcluidoEm(OffsetDateTime.now());
        fech = fechamentoRepository.save(fech);

        ResultadoCalculo r1 = new ResultadoCalculo(
                UUID.randomUUID(), UUID.randomUUID(), "MATRIC-MULTI",
                10, 75, 100, 1L,
                LocalDate.of(2026, 10, 5),
                new BigDecimal("1000.00"), new BigDecimal("0.0500"), new BigDecimal("50.00"),
                "FECHAMENTO_OFICIAL"
        );
        r1.setFechamentoId(fech.getId());
        resultadoCalculoRepository.save(r1);

        ResultadoCalculo r2 = new ResultadoCalculo(
                UUID.randomUUID(), UUID.randomUUID(), "MATRIC-MULTI",
                10, 88, 200, 2L,
                LocalDate.of(2026, 10, 6),
                new BigDecimal("2000.00"), new BigDecimal("0.0500"), new BigDecimal("100.00"),
                "FECHAMENTO_OFICIAL"
        );
        r2.setFechamentoId(fech.getId());
        resultadoCalculoRepository.save(r2);

        RelatorioFechamentoConsolidadoDTO relatorio = relatorioFechamentoService.gerarRelatorioCompetencia("2026-10");
        assertThat(relatorio.resumo().totalColaboradoresDistintos()).isEqualTo(1);
        assertThat(relatorio.porColaborador()).hasSize(2);
    }
}
