package com.bugbusters.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandRepository;
import com.bugbusters.backend.dto.interpretador.proposta.ValidarPropostaRequest;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionRepository;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "ai.service.url=http://localhost:8000")
class ValidarPropostaIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private BaseComissRepository baseComissRepository;

    @Autowired
    private com.bugbusters.backend.repository.LogCalculoRepository logCalculoRepository;

    @Autowired
    private com.bugbusters.backend.repository.ResultadoCalculoRepository resultadoCalculoRepository;

    @Autowired
    private com.bugbusters.backend.sales.SaleRepository saleRepository;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private Brand marca;
    private Position cargo;
    private Store loja1;
    private Store loja2;
    private Registration colaborador;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        regraRepository.deleteAll();
        campanhaRepository.deleteAll();

        marca = brandRepository.findByCode(10).orElseGet(() -> {
            Brand b = new Brand();
            b.setCode(10);
            b.setDescription("PRETO");
            return brandRepository.save(b);
        });

        cargo = positionRepository.findByCode(100).orElseGet(() -> {
            Position p = new Position();
            p.setCode(100);
            p.setDescription("VENDEDOR LOJA");
            return positionRepository.save(p);
        });

        loja1 = storeRepository.findByCode(1).orElseGet(() -> {
            Store s = new Store();
            s.setCode(1);
            s.setDescription("LOJA-1");
            return storeRepository.save(s);
        });

        loja2 = storeRepository.findByCode(2).orElseGet(() -> {
            Store s = new Store();
            s.setCode(2);
            s.setDescription("LOJA-2");
            return storeRepository.save(s);
        });

        colaborador = registrationRepository.findByRegistration("MATRIC-123").orElseGet(() -> {
            Registration r = new Registration();
            r.setRegistration("MATRIC-123");
            r.setStore(loja1);
            r.setPosition(cargo);
            r.setAdmissDate(LocalDate.of(2024, 1, 1));
            return registrationRepository.save(r);
        });

        baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(10, 100).orElseGet(() -> {
            BaseComiss bc = new BaseComiss(marca, cargo, new BigDecimal("0.0250"));
            bc.setReferenceMonth(LocalDate.of(2025, 1, 1));
            return baseComissRepository.save(bc);
        });
    }


    @Test
    @DisplayName("Deve rejeitar proposta com marca inexistente e taxa inválida")
    void deveRejeitarPropostaComErros() throws Exception {
        ValidarPropostaRequest request = new ValidarPropostaRequest(
                null,
                9999, // Marca inexistente
                "MARCA FANTASMA",
                100,
                "VENDEDOR LOJA",
                1,
                "ECOMMERCE",
                null,
                BigDecimal.ZERO, // Taxa inválida
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)
        );

        mockMvc.perform(post("/api/v1/interpretador/validar-proposta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida", is(false)))
                .andExpect(jsonPath("$.erros", hasSize(2)))
                .andExpect(jsonPath("$.erros", hasItem(containsString("Marca com código 9999"))))
                .andExpect(jsonPath("$.erros", hasItem(containsString("taxa de comissão deve ser um valor estritamente positivo"))));
    }

    @Test
    @DisplayName("Deve aceitar proposta válida enriquecendo referências cadastrais e taxa base")
    void deveValidarPropostaComSucesso() throws Exception {
        ValidarPropostaRequest request = new ValidarPropostaRequest(
                null,
                10,
                null,
                100,
                null,
                1,
                "ECOMMERCE",
                "MATRIC-123",
                new BigDecimal("0.0400"),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)
        );

        mockMvc.perform(post("/api/v1/interpretador/validar-proposta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida", is(true)))
                .andExpect(jsonPath("$.erros", empty()))
                .andExpect(jsonPath("$.avisos", empty()))
                .andExpect(jsonPath("$.referenciasValidadas", not(empty())))
                .andExpect(jsonPath("$.referenciasValidadas[*].tipo", hasItem("MARCA")))
                .andExpect(jsonPath("$.referenciasValidadas[*].tipo", hasItem("CARGO")))
                .andExpect(jsonPath("$.referenciasValidadas[*].tipo", hasItem("LOJA")))
                .andExpect(jsonPath("$.referenciasValidadas[*].tipo", hasItem("COLABORADOR")))
                .andExpect(jsonPath("$.referenciasValidadas[*].tipo", hasItem("TAXA_BASE_CONTRATUAL")));
    }

    @Test
    @DisplayName("Deve emitir aviso de divergência cadastral quando matrícula estiver alocada em loja diferente")
    void deveEmitirAvisoDivergenciaCadastral() throws Exception {
        // Colaborador MATRIC-123 é da Loja 1, mas a proposta especificou Loja 2
        ValidarPropostaRequest request = new ValidarPropostaRequest(
                null,
                10,
                "PRETO",
                100,
                "VENDEDOR LOJA",
                2, // Loja 2
                null,
                "MATRIC-123", // Alocado na Loja 1
                new BigDecimal("0.0400"),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)
        );

        mockMvc.perform(post("/api/v1/interpretador/validar-proposta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida", is(true)))
                .andExpect(jsonPath("$.erros", empty()))
                .andExpect(jsonPath("$.avisos", hasSize(1)))
                .andExpect(jsonPath("$.avisos[0]", containsString("Divergência cadastral: a matrícula MATRIC-123 está alocada na Loja 1, mas a proposta especifica a Loja 2")));
    }

    @Test
    @DisplayName("Deve acoplar a análise de concorrência de regras e avisar sobre regras idênticas")
    void deveIntegrarAnaliseConcorrenciaNaValidacao() throws Exception {
        Campanha c = new Campanha("Campanha Ativa", "Texto", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        c = campanhaRepository.save(c);

        Regra r = new Regra();
        r.setCampanha(c);
        r.setNome("Regra E-commerce 10");
        r.setCanal("ECOMMERCE");
        r.setCodMarca(10);
        r.setTaxa(new BigDecimal("0.0500"));
        r.setDataInicio(LocalDate.of(2026, 10, 1));
        r.setDataFim(LocalDate.of(2026, 10, 31));
        r.setStatus(StatusRegra.ATIVA);
        regraRepository.save(r);

        ValidarPropostaRequest request = new ValidarPropostaRequest(
                null,
                10,
                "PRETO",
                null,
                null,
                null,
                "ECOMMERCE",
                null,
                new BigDecimal("0.0500"),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 25)
        );

        mockMvc.perform(post("/api/v1/interpretador/validar-proposta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida", is(true)))
                .andExpect(jsonPath("$.avisos", hasItem(containsString("já existe uma regra exatamente idêntica"))))
                .andExpect(jsonPath("$.analiseConcorrencia.totalConcorrentes", is(1)))
                .andExpect(jsonPath("$.analiseConcorrencia.temIdentica", is(true)));
    }
}
