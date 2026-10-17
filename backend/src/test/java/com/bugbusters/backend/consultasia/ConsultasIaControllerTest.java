package com.bugbusters.backend.consultasia;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConsultasIaControllerTest {

    private static final String BASE = "/api/v1/consultas-ia";

    @Autowired private WebApplicationContext context;
    @Autowired private BrandRepository brandRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private BaseComissRepository baseComissRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private RegraRepository regraRepository;

    private MockMvc mockMvc;
    private Brand brand;
    private Store lojaCentro;
    private Position gerenteLoja;
    private Position gerenteQuiosque;
    private Registration registration;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        brand = new Brand();
        brand.setCode(9101);
        brand.setDescription("AZUL ESCURO");
        brand = brandRepository.save(brand);

        lojaCentro = novaLoja(9201, "LOJA CENTRO ÁGUAS");
        novaLoja(9202, "LOJA NORTE");

        gerenteLoja = novoCargo(9301, "GERENTE DE LOJA");
        gerenteQuiosque = novoCargo(9302, "GERENTE QUIOSQUE");

        registration = new Registration();
        registration.setRegistration("CONS-001");
        registration.setStore(lojaCentro);
        registration.setPosition(gerenteLoja);
        registration.setAdmissDate(LocalDate.of(2024, 1, 10));
        registration = registrationRepository.save(registration);
    }

    private Store novaLoja(int codigo, String descricao) {
        Store s = new Store();
        s.setCode(codigo);
        s.setDescription(descricao);
        return storeRepository.save(s);
    }

    private Position novoCargo(int codigo, String descricao) {
        Position p = new Position();
        p.setCode(codigo);
        p.setDescription(descricao);
        return positionRepository.save(p);
    }

    // ------------------------------------------------------------ catálogos

    @Test
    @DisplayName("Marcas: filtra por nome normalizado (sem acento, maiúsculas, contém) e por código")
    void deveConsultarMarcas() throws Exception {
        mockMvc.perform(get(BASE + "/marcas").param("nome", "azul esc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado", is(true)))
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].codigo", is(9101)))
                .andExpect(jsonPath("$.itens[0].descricao", is("AZUL ESCURO")));

        // marcas predefinidas do sistema também aparecem
        mockMvc.perform(get(BASE + "/marcas").param("codigo", "10"))
                .andExpect(jsonPath("$.encontrado", is(true)))
                .andExpect(jsonPath("$.itens[0].descricao", is("PRETO")));

        mockMvc.perform(get(BASE + "/marcas").param("nome", "inexistente-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado", is(false)))
                .andExpect(jsonPath("$.itens", hasSize(0)));
    }

    @Test
    @DisplayName("Lojas: ignora acentos no nome e devolve codMarca nulo")
    void deveConsultarLojas() throws Exception {
        mockMvc.perform(get(BASE + "/lojas").param("nome", "aguas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado", is(true)))
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].codigo", is(9201)))
                .andExpect(jsonPath("$.itens[0].codMarca").doesNotExist());

        mockMvc.perform(get(BASE + "/lojas").param("codigo", "9202"))
                .andExpect(jsonPath("$.itens[0].descricao", is("LOJA NORTE")));
    }

    @Test
    @DisplayName("Cargos: nome com mais de um candidato devolve vários itens (ambiguidade)")
    void deveConsultarCargosAmbiguos() throws Exception {
        mockMvc.perform(get(BASE + "/cargos").param("nome", "gerente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado", is(true)))
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].codigo", is(9301)))
                .andExpect(jsonPath("$.itens[1].codigo", is(9302)));
    }

    @Test
    @DisplayName("Cargos: nome no plural casa com a descrição no singular (ambiguidade preservada)")
    void deveConsultarCargosNoPlural() throws Exception {
        mockMvc.perform(get(BASE + "/cargos").param("nome", "gerentes"))
                .andExpect(jsonPath("$.encontrado", is(true)))
                .andExpect(jsonPath("$.itens", hasSize(2)));

        mockMvc.perform(get(BASE + "/cargos").param("nome", "gerentes de loja"))
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].codigo", is(9301)));

        mockMvc.perform(get(BASE + "/cargos").param("nome", "diretores"))
                .andExpect(jsonPath("$.encontrado", is(false)));
    }

    // ------------------------------------------------------------ matrícula

    @Test
    @DisplayName("Matrícula existente devolve loja, cargo e datas; inexistente devolve só encontrado=false")
    void deveConsultarMatricula() throws Exception {
        mockMvc.perform(get(BASE + "/matriculas/CONS-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado", is(true)))
                .andExpect(jsonPath("$.matricula", is("CONS-001")))
                .andExpect(jsonPath("$.codLoja", is(9201)))
                .andExpect(jsonPath("$.codCargo", is(9301)))
                .andExpect(jsonPath("$.descriCargo", is("GERENTE DE LOJA")))
                .andExpect(jsonPath("$.dataAdmissao", is("2024-01-10")))
                .andExpect(jsonPath("$.dataDemissao").doesNotExist());

        mockMvc.perform(get(BASE + "/matriculas/NAO-EXISTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado", is(false)))
                .andExpect(jsonPath("$.matricula").doesNotExist());
    }

    // ------------------------------------------------------------ bases

    @Test
    @DisplayName("Base: usa o mês pedido, cai para o mais recente e distingue ausência de base")
    void deveConsultarBase() throws Exception {
        BaseComiss base = new BaseComiss(brand, gerenteLoja, new BigDecimal("0.0275"));
        base.setReferenceMonth(LocalDate.of(2026, 8, 1));
        baseComissRepository.save(base);

        mockMvc.perform(get(BASE + "/bases").param("codMarca", "9101").param("codCargo", "9301").param("competencia", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado", is(true)))
                .andExpect(jsonPath("$.taxa", is(0.0275)))
                .andExpect(jsonPath("$.competencia", is("2026-08")))
                .andExpect(jsonPath("$.competenciaUsada", is("2026-08")));

        mockMvc.perform(get(BASE + "/bases").param("codMarca", "9101").param("codCargo", "9301").param("competencia", "2026-09"))
                .andExpect(jsonPath("$.encontrado", is(true)))
                .andExpect(jsonPath("$.competencia", is("2026-09")))
                .andExpect(jsonPath("$.competenciaUsada", is("2026-08")));

        mockMvc.perform(get(BASE + "/bases").param("codMarca", "9101").param("codCargo", "9302").param("competencia", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado", is(false)))
                .andExpect(jsonPath("$.taxa").doesNotExist());

        mockMvc.perform(get(BASE + "/bases").param("codMarca", "9999").param("codCargo", "9301").param("competencia", "2026-09"))
                .andExpect(jsonPath("$.encontrado", is(false)));
    }

    @Test
    @DisplayName("Base: competência inválida ou ausente é erro 400 (não 'sem dados')")
    void deveRejeitarCompetenciaInvalida() throws Exception {
        mockMvc.perform(get(BASE + "/bases").param("codMarca", "9101").param("codCargo", "9301").param("competencia", "09/2026"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/bases").param("codMarca", "9101").param("codCargo", "9301"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ concorrência

    private Regra novaRegra(Campanha campanha, Integer codMarca, Integer codLoja, Integer codCargo, String taxa,
                            LocalDate inicio, LocalDate fim, StatusRegra status) {
        Regra r = new Regra();
        r.setCampanha(campanha);
        r.setNome("R " + taxa);
        r.setCodMarca(codMarca);
        r.setCodLoja(codLoja);
        r.setCodCargo(codCargo);
        r.setTaxa(new BigDecimal(taxa));
        r.setDataInicio(inicio);
        r.setDataFim(fim);
        r.setStatus(status);
        return regraRepository.save(r);
    }

    private Campanha novaCampanha(String titulo) {
        Campanha c = new Campanha();
        c.setTitulo(titulo);
        c.setTextoOriginal("x");
        c.setDataInicio(LocalDate.of(2026, 9, 1));
        c.setDataFim(LocalDate.of(2026, 9, 30));
        c.setEstado(EstadoCampanha.ATIVA);
        return campanhaRepository.save(c);
    }

    @Test
    @DisplayName("Regras concorrentes: sobreposição de vigência e público, peso de especificidade e ignorarCampanhaId")
    void deveConsultarRegrasConcorrentes() throws Exception {
        Campanha a = novaCampanha("A");
        Campanha b = novaCampanha("B");
        Regra porLoja = novaRegra(a, null, 9201, null, "0.0400", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), StatusRegra.ATIVA);
        Regra porMarca = novaRegra(b, 9101, null, null, "0.0300", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), StatusRegra.ATIVA);
        Regra inativa = novaRegra(b, null, 9201, null, "0.0900", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), StatusRegra.INATIVA);
        Regra foraDoPeriodo = novaRegra(b, null, 9201, null, "0.0800", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), StatusRegra.ATIVA);

        String corpo = """
                {"condicoes":{"codLoja":9201,"codMarca":9101},"dataInicio":"2026-09-10","dataFim":"2026-09-20"}
                """;
        mockMvc.perform(post(BASE + "/regras-concorrentes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + porLoja.getId() + ")].especificidade").value(8))
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + porLoja.getId() + ")].campanhaId").value(String.valueOf(a.getId())))
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + porLoja.getId() + ")].taxa").value(0.04))
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + porMarca.getId() + ")].especificidade").value(1))
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + inativa.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + foraDoPeriodo.getId() + ")]").isEmpty());

        // marca diferente: a regra por marca 9101 não concorre
        String outraMarca = """
                {"condicoes":{"codLoja":9201,"codMarca":9999},"dataInicio":"2026-09-10","dataFim":"2026-09-20"}
                """;
        mockMvc.perform(post(BASE + "/regras-concorrentes").contentType(MediaType.APPLICATION_JSON).content(outraMarca))
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + porMarca.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + porLoja.getId() + ")]").isNotEmpty());

        // edição: a proposta substitui a campanha A
        String ignorando = """
                {"condicoes":{"codLoja":9201},"dataInicio":"2026-09-10","dataFim":"2026-09-20","ignorarCampanhaId":"%d"}
                """.formatted(a.getId());
        mockMvc.perform(post(BASE + "/regras-concorrentes").contentType(MediaType.APPLICATION_JSON).content(ignorando))
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + porLoja.getId() + ")]").isEmpty());
    }

    @Test
    @DisplayName("Regras concorrentes: faixas de valor que não se sobrepõem não concorrem")
    void deveConsiderarFaixaNaConcorrencia() throws Exception {
        Campanha a = novaCampanha("F");
        Regra faixaAlta = novaRegra(a, null, 9202, null, "0.0500", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), StatusRegra.ATIVA);
        faixaAlta.setValorMin(new BigDecimal("1000.00"));
        regraRepository.save(faixaAlta);

        String faixaBaixa = """
                {"condicoes":{"codLoja":9202},"dataInicio":"2026-09-01","dataFim":"2026-09-30","valorMin":"0.00","valorMax":"1000.00","maxInclusivo":false}
                """;
        mockMvc.perform(post(BASE + "/regras-concorrentes").contentType(MediaType.APPLICATION_JSON).content(faixaBaixa))
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + faixaAlta.getId() + ")]").isEmpty());

        String faixaSobreposta = """
                {"condicoes":{"codLoja":9202},"dataInicio":"2026-09-01","dataFim":"2026-09-30","valorMin":"0.00","valorMax":"1000.00"}
                """;
        mockMvc.perform(post(BASE + "/regras-concorrentes").contentType(MediaType.APPLICATION_JSON).content(faixaSobreposta))
                .andExpect(jsonPath("$.conflitos[?(@.idRegra==" + faixaAlta.getId() + ")]").isNotEmpty());
    }

    @Test
    @DisplayName("Regras concorrentes: dataInicio é obrigatória (400)")
    void deveExigirDataInicio() throws Exception {
        mockMvc.perform(post(BASE + "/regras-concorrentes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"condicoes\":{\"codLoja\":9201}}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ dados de simulação

    private void novaVenda(String valor, LocalDate data) {
        Sale s = new Sale();
        s.setId(UUID.randomUUID());
        s.setRegistration(registration);
        s.setStore(lojaCentro);
        s.setBrand(brand);
        s.setValue(new BigDecimal(valor));
        s.setSaleDate(data);
        s.setSaleChannel("ECOMMERCE");
        saleRepository.save(s);
    }

    @Test
    @DisplayName("Dados de simulação: paginação determinística, filtros e formato do contrato")
    void deveConsultarDadosSimulacao() throws Exception {
        novaVenda("100.00", LocalDate.of(2026, 9, 1));
        novaVenda("200.00", LocalDate.of(2026, 9, 2));
        novaVenda("300.00", LocalDate.of(2026, 9, 3));
        novaVenda("999.00", LocalDate.of(2026, 10, 1));
        saleRepository.flush();

        mockMvc.perform(get(BASE + "/dados-simulacao").param("competencia", "2026-09").param("matricula", "CONS-001")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].valorVenda", is(100.0)))
                .andExpect(jsonPath("$.content[0].dataVenda", is("2026-09-01")))
                .andExpect(jsonPath("$.content[0].matricula", is("CONS-001")))
                .andExpect(jsonPath("$.content[0].codLoja", is(9201)))
                .andExpect(jsonPath("$.content[0].codMarca", is(9101)))
                .andExpect(jsonPath("$.content[0].codCargo", is(9301)))
                .andExpect(jsonPath("$.content[0].canal", is("ECOMMERCE")))
                .andExpect(jsonPath("$.content[0].id").isString())
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.last", is(false)));

        mockMvc.perform(get(BASE + "/dados-simulacao").param("competencia", "2026-09").param("matricula", "CONS-001")
                        .param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].valorVenda", is(300.0)))
                .andExpect(jsonPath("$.last", is(true)));

        mockMvc.perform(get(BASE + "/dados-simulacao").param("competencia", "2026-09").param("matricula", "CONS-001")
                        .param("cargo", "9302"))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.last", is(true)));
    }

    @Test
    @DisplayName("Dados de simulação: competência ausente é erro 400")
    void deveExigirCompetencia() throws Exception {
        mockMvc.perform(get(BASE + "/dados-simulacao")).andExpect(status().isBadRequest());
    }
}
