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
import com.bugbusters.backend.dto.auditoria.AuditoriaResponseDTO;
import com.bugbusters.backend.dto.auditoria.RegistroAuditoriaDTO;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.importbase.ImportStatus;
import com.bugbusters.backend.model.ArquivoImportacao;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.ArquivoImportacaoRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.AuditoriaGeralService;
import com.bugbusters.backend.service.CalculoService;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class Back17AuditoriaEHistoricoCargasIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy springSecurityFilterChain;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService authService;

    @Autowired private AuditoriaGeralService auditoriaService;
    @Autowired private CalculoService calculoService;
    @Autowired private BrandRepository brandRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private BaseComissRepository baseComissRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private RegraRepository regraRepository;
    @Autowired private EnvioArquivoRepository envioArquivoRepository;
    @Autowired private ArquivoImportacaoRepository arquivoImportacaoRepository;

    private MockMvc secureMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Brand brand;
    private Store store;
    private Position position;
    private Registration registration;
    private Registration registrationClean;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        secureMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();

        brand = brandRepository.findByCode(91701).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(91701);
            b.setDescription("MARCA BACK17");
            return brandRepository.saveAndFlush(b);
        });

        store = storeRepository.findByCode(91701).orElseGet(() -> {
            Store s = new Store();
            s.setCode(91701);
            s.setDescription("LOJA BACK17");
            return storeRepository.saveAndFlush(s);
        });

        position = positionRepository.findByCode(91701).orElseGet(() -> {
            Position p = new Position();
            p.setCode(91701);
            p.setDescription("CARGO BACK17");
            return positionRepository.saveAndFlush(p);
        });

        registration = registrationRepository.findByRegistration("MATRIC-BACK17-1").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-BACK17-1");
            r.setPosition(position);
            r.setStore(store);
            r.setAdmissDate(LocalDate.of(2020, 1, 1));
            return registrationRepository.saveAndFlush(r);
        });

        registrationClean = registrationRepository.findByRegistration("MATRIC-BACK17-CLEAN").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-BACK17-CLEAN");
            r.setPosition(position);
            r.setStore(store);
            r.setAdmissDate(LocalDate.of(2020, 1, 1));
            return registrationRepository.saveAndFlush(r);
        });

        LocalDate ref202403 = LocalDate.of(2024, 3, 1);
        baseComissRepository.findByBrandIdAndPositionIdAndReferenceMonth(brand.getId(), position.getId(), ref202403)
                .orElseGet(() -> {
                    BaseComiss bc = new BaseComiss(brand, position, new BigDecimal("0.0400"));
                    bc.setReferenceMonth(ref202403);
                    return baseComissRepository.saveAndFlush(bc);
                });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Usuario garantirUsuario(String email, String nome, PapelUsuario papel) {
        return usuarioRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNome(nome);
            u.setEmail(email);
            u.setPapel(papel);
            u.setSenhaHash(passwordEncoder.encode("Senha@123"));
            return usuarioRepository.saveAndFlush(u);
        });
    }

    private String loginComo(Usuario u) {
        return authService.login(new LoginRequest(u.getEmail(), "Senha@123")).token();
    }

    private void autenticarContexto(Usuario u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        u,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getPapel().name()))
                )
        );
    }

    @Test
    @DisplayName("BACK-17: Autorização hierárquica no servidor para auditoria, usuários, detalhes e logs de cálculo")
    void autorizacaoHierarquicaDeLogsNoServidorEProtecaoContraFiltrosForjados() throws Exception {
        String suf = UUID.randomUUID().toString().substring(0, 8);
        Usuario admin = garantirUsuario("back17.admin." + suf + "@teste.local", "Admin Back17", PapelUsuario.ADMINISTRADOR);
        Usuario gerente = garantirUsuario("back17.gerente." + suf + "@teste.local", "Gerente Back17", PapelUsuario.GERENTE_VENDAS);
        Usuario auditor = garantirUsuario("back17.auditor." + suf + "@teste.local", "Auditor Back17", PapelUsuario.AUDITOR);
        Usuario negocios = garantirUsuario("back17.negocios." + suf + "@teste.local", "Negocios Back17", PapelUsuario.ANALISTA_NEGOCIOS);
        Usuario codigo = garantirUsuario("back17.codigo." + suf + "@teste.local", "Codigo Back17", PapelUsuario.ANALISTA_CODIGO);

        String tkAdmin = loginComo(admin);
        String tkGerente = loginComo(gerente);
        String tkAuditor = loginComo(auditor);
        String tkNegocios = loginComo(negocios);
        String tkCodigo = loginComo(codigo);

        AuditoriaResponseDTO evAdmin = auditoriaService.registrarAcao(new RegistroAuditoriaDTO(
                admin.getEmail(), PapelUsuario.ADMINISTRADOR.name(), TipoAcaoAuditoria.ACAO_OPERACIONAL,
                TipoEntidadeAuditoria.CAMPANHA, "ADM-" + suf, 1, "Evento administrativo " + suf,
                null, null, null, null, true, null));
        AuditoriaResponseDTO evGerente = auditoriaService.registrarAcao(new RegistroAuditoriaDTO(
                gerente.getEmail(), PapelUsuario.GERENTE_VENDAS.name(), TipoAcaoAuditoria.DEVOLUCAO_ESTEIRA,
                TipoEntidadeAuditoria.ESTEIRA, "GER-" + suf, 1, "Evento gerente " + suf,
                null, null, null, null, true, null));
        AuditoriaResponseDTO evAuditor = auditoriaService.registrarAcao(new RegistroAuditoriaDTO(
                auditor.getEmail(), PapelUsuario.AUDITOR.name(), TipoAcaoAuditoria.EXPORTACAO_RELATORIO,
                TipoEntidadeAuditoria.RELATORIO, "AUD-" + suf, 1, "Evento auditor " + suf,
                null, null, null, null, true, null));
        AuditoriaResponseDTO evNegocios = auditoriaService.registrarAcao(new RegistroAuditoriaDTO(
                negocios.getEmail(), PapelUsuario.ANALISTA_NEGOCIOS.name(), TipoAcaoAuditoria.CRIACAO_CAMPANHA,
                TipoEntidadeAuditoria.CAMPANHA, "NEG-" + suf, 1, "Evento negocios " + suf,
                null, null, null, null, true, null));
        AuditoriaResponseDTO evCodigo = auditoriaService.registrarAcao(new RegistroAuditoriaDTO(
                codigo.getEmail(), PapelUsuario.ANALISTA_CODIGO.name(), TipoAcaoAuditoria.APROVACAO_CODIGO_CAMPANHA,
                TipoEntidadeAuditoria.CAMPANHA_VERSAO, "COD-" + suf, 1, "Evento codigo " + suf,
                null, null, null, null, true, null));
        AuditoriaResponseDTO evSistema = auditoriaService.registrarAcao(new RegistroAuditoriaDTO(
                "SISTEMA", "SISTEMA", TipoAcaoAuditoria.ACAO_OPERACIONAL,
                TipoEntidadeAuditoria.CALCULO, "SYS-" + suf, 1, "Evento sistema " + suf,
                null, null, null, null, true, null));

        // 1. Analistas são bloqueados em todas as rotas de logs e auditoria
        for (String tkAnalista : new String[]{tkNegocios, tkCodigo}) {
            secureMvc.perform(get("/api/v1/auditoria").header("Authorization", "Bearer " + tkAnalista))
                    .andExpect(status().isForbidden());
            secureMvc.perform(get("/api/v1/auditoria/{id}", evNegocios.id()).header("Authorization", "Bearer " + tkAnalista))
                    .andExpect(status().isForbidden());
            secureMvc.perform(get("/api/v1/auditoria/usuarios").header("Authorization", "Bearer " + tkAnalista))
                    .andExpect(status().isForbidden());
            secureMvc.perform(get("/api/v1/auditoria/usuarios/{u}", negocios.getEmail()).header("Authorization", "Bearer " + tkAnalista))
                    .andExpect(status().isForbidden());
            secureMvc.perform(get("/api/v1/auditoria/resumo").header("Authorization", "Bearer " + tkAnalista))
                    .andExpect(status().isForbidden());
            secureMvc.perform(get("/api/v1/auditoria/exportar").header("Authorization", "Bearer " + tkAnalista))
                    .andExpect(status().isForbidden());
            secureMvc.perform(get("/api/v1/logs-calculo").header("Authorization", "Bearer " + tkAnalista))
                    .andExpect(status().isForbidden());
            secureMvc.perform(get("/api/v1/logs-calculo/usuarios/{u}", negocios.getEmail()).header("Authorization", "Bearer " + tkAnalista))
                    .andExpect(status().isForbidden());
        }

        // 2. Gerente e Auditor não acessam /api/v1/usuarios (exclusivo ADMIN), mas usam /api/v1/auditoria/usuarios
        secureMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isForbidden());

        // 3. Gerente vê apenas ações dos dois tipos de analistas (ANALISTA_NEGOCIOS e ANALISTA_CODIGO)
        MvcResult resGerente = secureMvc.perform(get("/api/v1/auditoria")
                        .param("busca", suf)
                        .param("size", "50")
                        .header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isOk())
                .andReturn();
        Set<String> perfisGerente = extrairCampo(resGerente, "content", "perfilUsuario");
        assertThat(perfisGerente).containsExactlyInAnyOrder(PapelUsuario.ANALISTA_NEGOCIOS.name(), PapelUsuario.ANALISTA_CODIGO.name());

        // Filtro forjado pelo gerente tentando enxergar admin retorna vazio; consulta direta retorna 403
        secureMvc.perform(get("/api/v1/auditoria")
                        .param("perfilUsuario", "ADMINISTRADOR")
                        .param("busca", suf)
                        .header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        secureMvc.perform(get("/api/v1/auditoria/{id}", evAdmin.id()).header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/{id}", evGerente.id()).header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/usuarios/{u}", gerente.getEmail()).header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/usuarios/{u}", admin.getEmail()).header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/logs-calculo/usuarios/{u}", admin.getEmail()).header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/{id}", evNegocios.id()).header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evNegocios.id().toString()));
        secureMvc.perform(get("/api/v1/auditoria/usuarios/{u}", codigo.getEmail()).header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entidadeId").value("COD-" + suf));

        MvcResult usersGerente = secureMvc.perform(get("/api/v1/auditoria/usuarios")
                        .param("busca", suf)
                        .header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isOk())
                .andReturn();
        Set<String> papeisUsersGerente = extrairCampo(usersGerente, "content", "papel");
        assertThat(papeisUsersGerente).containsExactlyInAnyOrder(PapelUsuario.ANALISTA_NEGOCIOS.name(), PapelUsuario.ANALISTA_CODIGO.name());

        // 4. Auditor vê analistas e gerente, mas nunca administrador, auditor ou SISTEMA
        MvcResult resAuditor = secureMvc.perform(get("/api/v1/auditoria")
                        .param("busca", suf)
                        .param("size", "50")
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andReturn();
        Set<String> perfisAuditor = extrairCampo(resAuditor, "content", "perfilUsuario");
        assertThat(perfisAuditor).containsExactlyInAnyOrder(
                PapelUsuario.ANALISTA_NEGOCIOS.name(),
                PapelUsuario.ANALISTA_CODIGO.name(),
                PapelUsuario.GERENTE_VENDAS.name()
        );
        secureMvc.perform(get("/api/v1/auditoria/{id}", evGerente.id()).header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evGerente.id().toString()));
        secureMvc.perform(get("/api/v1/auditoria/{id}", evAdmin.id()).header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/{id}", evAuditor.id()).header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/{id}", evSistema.id()).header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/usuarios/{u}", admin.getEmail()).header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/usuarios/SISTEMA").header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/usuarios/SISTEMA").header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/logs-calculo/usuarios/SISTEMA").header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isForbidden());
        secureMvc.perform(get("/api/v1/auditoria/usuarios/{u}", gerente.getEmail()).header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entidadeId").value("GER-" + suf));

        secureMvc.perform(get("/api/v1/auditoria/resumo")
                        .param("dataInicio", "2026-12-01T00:00:00Z")
                        .param("dataFim", "2026-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isBadRequest());
        secureMvc.perform(get("/api/v1/auditoria/exportar")
                        .param("dataInicio", "2026-12-01T00:00:00Z")
                        .param("dataFim", "2026-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isBadRequest());

        MvcResult resumoAuditor = secureMvc.perform(get("/api/v1/auditoria/resumo")
                        .param("usuario", suf)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(resumoAuditor.getResponse().getContentAsString()).get("totalRegistros").asLong())
                .isEqualTo(3L);

        String csvAuditor = secureMvc.perform(get("/api/v1/auditoria/exportar")
                        .param("busca", suf)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvAuditor).contains("GER-" + suf, "NEG-" + suf, "COD-" + suf)
                .doesNotContain("ADM-" + suf, "AUD-" + suf, "SYS-" + suf);

        // 5. Administrador vê tudo (incluindo admin, auditor e SISTEMA)
        MvcResult resAdmin = secureMvc.perform(get("/api/v1/auditoria")
                        .param("busca", suf)
                        .param("size", "50")
                        .header("Authorization", "Bearer " + tkAdmin))
                .andExpect(status().isOk())
                .andReturn();
        Set<String> perfisAdmin = extrairCampo(resAdmin, "content", "perfilUsuario");
        assertThat(perfisAdmin).containsExactlyInAnyOrder(
                PapelUsuario.ADMINISTRADOR.name(),
                PapelUsuario.GERENTE_VENDAS.name(),
                PapelUsuario.AUDITOR.name(),
                PapelUsuario.ANALISTA_NEGOCIOS.name(),
                PapelUsuario.ANALISTA_CODIGO.name(),
                "SISTEMA"
        );
        secureMvc.perform(get("/api/v1/auditoria/{id}", evSistema.id()).header("Authorization", "Bearer " + tkAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("SISTEMA"));
        secureMvc.perform(get("/api/v1/auditoria/protocolo/{p}", evAdmin.protocolo()).header("Authorization", "Bearer " + tkAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evAdmin.id().toString()));
    }

    @Test
    @DisplayName("BACK-17: Relacionamentos com campanha, arquivo e cálculo + execução assíncrona atribuída ao usuário solicitante")
    void deveExporRelacionamentosComCampanhaArquivoECalculoEDadosUsuarioEmExecucaoAssincrona() throws Exception {
        String suf = UUID.randomUUID().toString().substring(0, 8);
        Usuario admin = garantirUsuario("back17.rel.admin." + suf + "@teste.local", "Admin Rel", PapelUsuario.ADMINISTRADOR);
        Usuario gerente = garantirUsuario("back17.rel.gerente." + suf + "@teste.local", "Gerente Rel", PapelUsuario.GERENTE_VENDAS);
        Usuario auditor = garantirUsuario("back17.rel.auditor." + suf + "@teste.local", "Auditor Rel", PapelUsuario.AUDITOR);
        Usuario negocios = garantirUsuario("back17.rel.negocios." + suf + "@teste.local", "Negocios Rel", PapelUsuario.ANALISTA_NEGOCIOS);

        String tkAdmin = loginComo(admin);
        String tkGerente = loginComo(gerente);
        String tkAuditor = loginComo(auditor);
        String tkNegocios = loginComo(negocios);

        // Cria campanha como analista de negócios (gera CRIACAO_CAMPANHA)
        RegraItemRequest regraReq = new RegraItemRequest(
                "bloco-rel-" + suf,
                "Regra BACK17 7%",
                "Regra Campanha Rel " + suf,
                "LOJA_FISICA",
                brand.getCode(), brand.getDescription(), store.getCode(), position.getCode(), position.getDescription(),
                null, null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA,
                null, null, null, null,
                new BigDecimal("0.0700"),
                "7% loja física",
                "taxa = 0.07",
                null,
                true,
                null, null, null
        );
        CampanhaRequest campReq = new CampanhaRequest(
                "Campanha Back17 Relacionamentos " + suf,
                "Texto campanha relacionamentos",
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 3, 31),
                EstadoCampanha.DRAFT,
                List.of(regraReq),
                null,
                new BigDecimal("100000.00"),
                null,
                true,
                negocios.getEmail(),
                "Orcamento rel"
        );
        MvcResult campRes = secureMvc.perform(post("/api/v1/campanhas")
                        .header("Authorization", "Bearer " + tkNegocios)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(campReq)))
                .andExpect(status().isCreated())
                .andReturn();
        long campanhaId = objectMapper.readTree(campRes.getResponse().getContentAsString()).get("id").asLong();

        // Cria EnvioArquivo de origem e venda em 2024-03 para testar hidratação de arquivo e campanha no LogCalculo
        String idLoteOrigem = "lote-rel-" + suf;
        byte[] dummy = ("conteudo-" + suf).getBytes(StandardCharsets.UTF_8);
        ArquivoImportacao arq = arquivoImportacaoRepository.saveAndFlush(new ArquivoImportacao(
                sha256(dummy), "vendas_rel_" + suf + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                dummy.length, dummy, gerente.getEmail()
        ));
        EnvioArquivo envio = new EnvioArquivo();
        envio.setIdLote(idLoteOrigem);
        envio.setReportId(917_000L + Math.abs(suf.hashCode() % 100_000));
        envio.setNomeArquivo("vendas_rel_" + suf + ".xlsx");
        envio.setTipoBase("SALES");
        envio.setCompetencia("2024-03");
        envio.setStatus("CONCLUIDO");
        envio.setTotalLinhas(1);
        envio.setLinhasValidas(1);
        envio.setCriadoPor(gerente.getEmail());
        envio.setPerfilCriador(PapelUsuario.GERENTE_VENDAS.name());
        envio.setArquivoImportacaoId(arq.getId());
        envio.setCriadoEm(OffsetDateTime.now());
        envio = envioArquivoRepository.saveAndFlush(envio);

        Sale vendaAnalista = new Sale();
        vendaAnalista.setBrand(brand);
        vendaAnalista.setStore(store);
        vendaAnalista.setRegistration(registration);
        vendaAnalista.setSaleChannel("LOJA_FISICA");
        vendaAnalista.setSaleDate(LocalDate.of(2024, 3, 12));
        vendaAnalista.setValue(new BigDecimal("1000.00"));
        vendaAnalista.setIdLoteOrigem(idLoteOrigem);
        vendaAnalista = saleRepository.saveAndFlush(vendaAnalista);

        // Ativa regra para o motor de cálculo (simula campanha vigente ligada à regra)
        Campanha campEnt = campanhaRepository.findById(campanhaId).orElseThrow();
        campEnt.setEstado(EstadoCampanha.ATIVA);
        campanhaRepository.saveAndFlush(campEnt);
        Regra regraSalva = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanhaId).get(0);
        regraSalva.setStatus(com.bugbusters.backend.dto.regra.StatusRegra.ATIVA);
        regraRepository.saveAndFlush(regraSalva);

        // Calcula venda individual como ANALISTA_NEGOCIOS -> vincula campanha + arquivo + cálculo
        autenticarContexto(negocios);
        var calcInd = calculoService.calcularVendaIndividualPorId(vendaAnalista.getId());
        SecurityContextHolder.clearContext();

        // Cria segunda venda em 2024-03 e dispara execução assíncrona via HTTP como GERENTE_VENDAS
        Sale vendaGerente = new Sale();
        vendaGerente.setBrand(brand);
        vendaGerente.setStore(store);
        vendaGerente.setRegistration(registration);
        vendaGerente.setSaleChannel("LOJA_FISICA");
        vendaGerente.setSaleDate(LocalDate.of(2024, 3, 18));
        vendaGerente.setValue(new BigDecimal("2000.00"));
        vendaGerente.setIdLoteOrigem(idLoteOrigem);
        saleRepository.saveAndFlush(vendaGerente);

        MvcResult execSolicRes = secureMvc.perform(post("/api/v1/execucoes/calculo")
                        .header("Authorization", "Bearer " + tkGerente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competencia\":\"2024-03\",\"recalcular\":false,\"chaveIdempotencia\":\"back17-exec-" + suf + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String execId = objectMapper.readTree(execSolicRes.getResponse().getContentAsString()).get("id").asText();

        JsonNode execFinal = aguardarExecucaoConcluida(execId, tkGerente);
        assertThat(execFinal.get("status").asText()).isEqualTo("CONCLUIDA");

        // Gerente consulta logs de cálculo do analista com relacionamento hidratado
        secureMvc.perform(get("/api/v1/logs-calculo/usuarios/{u}", negocios.getEmail())
                        .param("campanhaId", String.valueOf(campanhaId))
                        .header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].protocolo").value(calcInd.protocoloCalculo().toString()))
                .andExpect(jsonPath("$.content[0].usuarioExecutor").value(negocios.getEmail()))
                .andExpect(jsonPath("$.content[0].perfilExecutor").value(PapelUsuario.ANALISTA_NEGOCIOS.name()))
                .andExpect(jsonPath("$.content[0].relacionamentos.campanha.campanhaId").value(campanhaId))
                .andExpect(jsonPath("$.content[0].relacionamentos.campanha.titulo").value("Campanha Back17 Relacionamentos " + suf))
                .andExpect(jsonPath("$.content[0].relacionamentos.arquivo.reportId").value(envio.getReportId()))
                .andExpect(jsonPath("$.content[0].relacionamentos.arquivo.nomeArquivo").value("vendas_rel_" + suf + ".xlsx"));

        // Auditor consulta execução assíncrona feita pelo GERENTE_VENDAS (ExecucaoCalculoWorker propagou usuarioExecutor!)
        secureMvc.perform(get("/api/v1/logs-calculo/usuarios/{u}", gerente.getEmail())
                        .param("idLoteOrigem", idLoteOrigem)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].usuarioExecutor").value(gerente.getEmail()))
                .andExpect(jsonPath("$.content[0].perfilExecutor").value(PapelUsuario.GERENTE_VENDAS.name()))
                .andExpect(jsonPath("$.content[0].relacionamentos.campanha.campanhaId").value(campanhaId))
                .andExpect(jsonPath("$.content[0].relacionamentos.arquivo.reportId").value(envio.getReportId()));

        // Auditoria geral do analista expõe campanha e protocolo
        secureMvc.perform(get("/api/v1/auditoria/usuarios/{u}", negocios.getEmail())
                        .param("campanhaId", String.valueOf(campanhaId))
                        .header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[0].relacionamentos.campanha.campanhaId").value(campanhaId));

        // Auditoria geral da execução assíncrona contém execucaoCalculoId para o gerente (visível a auditor e admin)
        secureMvc.perform(get("/api/v1/auditoria")
                        .param("execucaoCalculoId", execId)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].usuario").value(gerente.getEmail()))
                .andExpect(jsonPath("$.content[0].relacionamentos.calculo.execucaoCalculoId").value(execId))
                .andExpect(jsonPath("$.content[0].relacionamentos.calculo.competencia").value("2024-03"));
        secureMvc.perform(get("/api/v1/auditoria/resumo")
                        .param("execucaoCalculoId", execId)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistros").value(2));
        String csvExec = secureMvc.perform(get("/api/v1/auditoria/exportar")
                        .param("execucaoCalculoId", execId)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvExec).contains(execId, "2024-03");

        // Admin visualiza resumo de usuários consolidando auditoria + cálculo
        secureMvc.perform(get("/api/v1/auditoria/usuarios")
                        .param("busca", negocios.getEmail())
                        .header("Authorization", "Bearer " + tkAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(negocios.getEmail()))
                .andExpect(jsonPath("$.content[0].totalEventosAuditoria").isNumber())
                .andExpect(jsonPath("$.content[0].totalLogsCalculo").value(1));
    }

    @Test
    @DisplayName("BACK-17: Histórico permanente de cargas, falhas impeditivas, advertências, exclusões e download de arquivo")
    void devePersistirHistoricoPermanenteDeCargasFalhasAdvertenciasExclusoesEArquivo() throws Exception {
        String suf = UUID.randomUUID().toString().substring(0, 8);
        Usuario admin = garantirUsuario("back17.imp.admin." + suf + "@teste.local", "Admin Imp", PapelUsuario.ADMINISTRADOR);
        Usuario gerente = garantirUsuario("back17.imp.gerente." + suf + "@teste.local", "Gerente Imp", PapelUsuario.GERENTE_VENDAS);
        Usuario auditor = garantirUsuario("back17.imp.auditor." + suf + "@teste.local", "Auditor Imp", PapelUsuario.AUDITOR);

        String tkAdmin = loginComo(admin);
        String tkGerente = loginComo(gerente);
        String tkAuditor = loginComo(auditor);

        // 1. Upload de SALES com 1 linha válida + 1 advertência (matrícula inexistente)
        byte[] xlsxParcial = gerarPlanilhaVendas(List.of(
                new Object[]{"2024-04-10", brand.getCode(), brand.getDescription(), store.getCode(), store.getDescription(), registration.getRegistration(), 1000.0},
                new Object[]{"2024-04-11", brand.getCode(), brand.getDescription(), store.getCode(), store.getDescription(), "INEXIST-" + suf, 500.0}
        ));
        MvcResult upParcial = secureMvc.perform(multipart("/api/v1/imports/upload")
                        .file(new MockMultipartFile("file", "vendas_parcial_" + suf + ".xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxParcial))
                        .param("importType", "SALES")
                        .param("competencia", "2024-04")
                        .header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isAccepted())
                .andReturn();
        long reportParcial = objectMapper.readTree(upParcial.getResponse().getContentAsString()).get("reportId").asLong();
        aguardarLoteFinalizado(reportParcial, tkGerente, ImportStatus.CONCLUIDO);

        // 2. Upload de SALES com erro IMPEDITIVO (valor negativo) -> FALHOU, mas persiste validação impeditiva + arquivo original
        byte[] xlsxFalha = gerarPlanilhaVendas(List.<Object[]>of(
                new Object[]{"2024-04-15", brand.getCode(), brand.getDescription(), store.getCode(), store.getDescription(), registration.getRegistration(), -250.0}
        ));
        MvcResult upFalha = secureMvc.perform(multipart("/api/v1/imports/upload")
                        .file(new MockMultipartFile("file", "vendas_falha_" + suf + ".xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxFalha))
                        .param("importType", "SALES")
                        .param("competencia", "2024-04")
                        .header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isAccepted())
                .andReturn();
        long reportFalha = objectMapper.readTree(upFalha.getResponse().getContentAsString()).get("reportId").asLong();
        JsonNode statusFalha = aguardarLoteFinalizado(reportFalha, tkGerente, ImportStatus.FALHOU);
        String msgOriginalFalha = statusFalha.get("mensagem").asText();
        assertThat(msgOriginalFalha).isNotBlank();

        // 3. Upload de SALES limpo -> CONCLUIDO -> excluído logicamente por admin (com usuário forjado no JSON)
        byte[] xlsxExclusao = gerarPlanilhaVendas(List.<Object[]>of(
                new Object[]{"2024-04-20", brand.getCode(), brand.getDescription(), store.getCode(), store.getDescription(), registrationClean.getRegistration(), 800.0}
        ));
        MvcResult upExclusao = secureMvc.perform(multipart("/api/v1/imports/upload")
                        .file(new MockMultipartFile("file", "vendas_excluir_" + suf + ".xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxExclusao))
                        .param("importType", "SALES")
                        .param("competencia", "2024-04")
                        .header("Authorization", "Bearer " + tkGerente))
                .andExpect(status().isAccepted())
                .andReturn();
        long reportExclusao = objectMapper.readTree(upExclusao.getResponse().getContentAsString()).get("reportId").asLong();
        aguardarLoteFinalizado(reportExclusao, tkGerente, ImportStatus.CONCLUIDO);

        // Auditor consegue consultar histórico e baixar arquivo, mas é bloqueado ao tentar excluir (403)
        secureMvc.perform(delete("/api/v1/imports/{reportId}", reportExclusao)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isForbidden());

        // Exclusão pelo admin sobrescreve o campo spoofado no body com o e-mail do JWT
        secureMvc.perform(delete("/api/v1/imports/{reportId}", reportExclusao)
                        .header("Authorization", "Bearer " + tkAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"forjado@evil.com\",\"motivo\":\"Limpeza planejada " + suf + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXCLUIDO"))
                .andExpect(jsonPath("$.registrosRemovidos").value(1))
                .andExpect(jsonPath("$.excluidoPor").value(admin.getEmail()))
                .andExpect(jsonPath("$.motivo").value("Limpeza planejada " + suf))
                .andExpect(jsonPath("$.protocoloAuditoria").isNotEmpty());

        // Exclusão lógica do lote FALHOU preserva a mensagem original da falha e todas as validações persistidas
        secureMvc.perform(delete("/api/v1/imports/{reportId}", reportFalha)
                        .header("Authorization", "Bearer " + tkAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Descarte da falha " + suf + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXCLUIDO"))
                .andExpect(jsonPath("$.excluidoPor").value(admin.getEmail()));

        // Auditor consulta histórico permanente com filtros e detalhes persistidos
        secureMvc.perform(get("/api/v1/imports")
                        .param("competencia", "2024-04")
                        .param("somenteComInconsistencias", "true")
                        .param("busca", suf)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Detalhe do lote parcial (ADVERTENCIA preservada)
        secureMvc.perform(get("/api/v1/imports/lotes/{reportId}", reportParcial)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadoPor").value(gerente.getEmail()))
                .andExpect(jsonPath("$.perfilCriador").value(PapelUsuario.GERENTE_VENDAS.name()))
                .andExpect(jsonPath("$.totalInconsistencias").value(1))
                .andExpect(jsonPath("$.totalAdvertencias").value(1))
                .andExpect(jsonPath("$.inconsistencias[0].severidade").value("ADVERTENCIA"));

        // Detalhe e validações paginadas do lote FALHOU mesmo após marcado EXCLUIDO
        secureMvc.perform(get("/api/v1/imports/lotes/{reportId}", reportFalha)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXCLUIDO"))
                .andExpect(jsonPath("$.mensagem").value(msgOriginalFalha))
                .andExpect(jsonPath("$.motivoExclusao").value("Descarte da falha " + suf))
                .andExpect(jsonPath("$.excluidoPor").value(admin.getEmail()))
                .andExpect(jsonPath("$.perfilExclusao").value(PapelUsuario.ADMINISTRADOR.name()))
                .andExpect(jsonPath("$.totalImpeditivos").value(1))
                .andExpect(jsonPath("$.inconsistencias[0].severidade").value("IMPEDITIVO"));

        secureMvc.perform(get("/api/v1/imports/{reportId}/validacoes", reportFalha)
                        .param("severidade", "IMPEDITIVO")
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].severidade").value("IMPEDITIVO"));

        // Download do arquivo original preservado após falha e após exclusão
        byte[] baixadoFalha = secureMvc.perform(get("/api/v1/imports/{reportId}/arquivo", reportFalha)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("vendas_falha_" + suf + ".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(baixadoFalha).isEqualTo(xlsxFalha);

        byte[] baixadoExcluido = secureMvc.perform(get("/api/v1/imports/{reportId}/arquivo", reportExclusao)
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(baixadoExcluido).isEqualTo(xlsxExclusao);

        // Eventos IMPORTACAO_BASE (sucesso e falha) e EXCLUSAO_BASE registrados na AuditoriaGeral
        secureMvc.perform(get("/api/v1/auditoria")
                        .param("reportId", String.valueOf(reportFalha))
                        .param("tipoAcao", "IMPORTACAO_BASE")
                        .param("sucesso", "false")
                        .header("Authorization", "Bearer " + tkAuditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].relacionamentos.arquivo.reportId").value(reportFalha));

        secureMvc.perform(get("/api/v1/auditoria")
                        .param("reportId", String.valueOf(reportExclusao))
                        .param("tipoAcao", "EXCLUSAO_BASE")
                        .header("Authorization", "Bearer " + tkAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].usuario").value(admin.getEmail()))
                .andExpect(jsonPath("$.content[0].relacionamentos.arquivo.excluido").value(true));
    }

    private Set<String> extrairCampo(MvcResult result, String arrayField, String itemField) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString()).get(arrayField);
        Set<String> valores = new HashSet<>();
        if (root != null && root.isArray()) {
            for (JsonNode item : root) {
                JsonNode f = item.get(itemField);
                if (f != null && !f.isNull()) {
                    valores.add(f.asText());
                }
            }
        }
        return valores;
    }

    private JsonNode aguardarExecucaoConcluida(String execId, String token) throws Exception {
        long fim = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < fim) {
            MvcResult res = secureMvc.perform(get("/api/v1/execucoes/calculo/{id}", execId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
            String st = node.get("status").asText();
            if ("CONCLUIDA".equals(st) || "FALHOU".equals(st)) {
                return node;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timeout aguardando execução " + execId);
    }

    private JsonNode aguardarLoteFinalizado(long reportId, String token, ImportStatus esperado) throws Exception {
        long fim = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < fim) {
            MvcResult res = secureMvc.perform(get("/api/v1/imports/{reportId}", reportId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
            String st = node.get("status").asText();
            if (ImportStatus.CONCLUIDO.name().equals(st) || ImportStatus.FALHOU.name().equals(st)) {
                assertThat(st).isEqualTo(esperado.name());
                return node;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timeout aguardando lote " + reportId);
    }

    private byte[] gerarPlanilhaVendas(List<Object[]> linhas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Vendas");
            Row header = sheet.createRow(0);
            String[] cols = {"DATA_VENDA", "COD_MARCA", "DESC_MARCA", "COD_LOJA", "DESC_LOJA", "MATRICULA", "VALOR"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            int rIdx = 1;
            for (Object[] vals : linhas) {
                Row row = sheet.createRow(rIdx++);
                row.createCell(0).setCellValue((String) vals[0]);
                row.createCell(1).setCellValue(((Number) vals[1]).doubleValue());
                row.createCell(2).setCellValue((String) vals[2]);
                row.createCell(3).setCellValue(((Number) vals[3]).doubleValue());
                row.createCell(4).setCellValue((String) vals[4]);
                row.createCell(5).setCellValue((String) vals[5]);
                row.createCell(6).setCellValue(((Number) vals[6]).doubleValue());
            }
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }
}
