package com.bugbusters.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

import com.bugbusters.backend.dto.regra.ConsultaConcorrenciaRequest;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.dto.regra.TipoConcorrenciaRegra;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "ai.service.url=http://localhost:8000")
class RegraConcorrenciaIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private RegraRepository regraRepository;

    @Autowired
    private CampanhaRepository campanhaRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());


    private Campanha campanhaExistente;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        regraRepository.deleteAll();
        campanhaRepository.deleteAll();

        campanhaExistente = new Campanha("Campanha Primavera 2026", "Texto original", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 11, 30));
        campanhaExistente = campanhaRepository.save(campanhaExistente);
    }

    @Test
    @DisplayName("Deve identificar regra IDENTICA quando todas as dimensões e período coincidirem")
    void deveIdentificarRegraIdentica() throws Exception {
        Regra r = new Regra();
        r.setCampanha(campanhaExistente);
        r.setNome("Regra E-commerce Marca 10");
        r.setCanal("ECOMMERCE");
        r.setCodMarca(10);
        r.setDescrMarca("PRETO");
        r.setCodCargo(100);
        r.setTaxa(new BigDecimal("0.0500"));
        r.setDataInicio(LocalDate.of(2026, 10, 1));
        r.setDataFim(LocalDate.of(2026, 10, 31));
        r.setStatus(StatusRegra.ATIVA);
        regraRepository.save(r);

        ConsultaConcorrenciaRequest request = new ConsultaConcorrenciaRequest(
                null,
                10,
                100,
                null,
                "ECOMMERCE",
                null,
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 20)
        );

        mockMvc.perform(post("/api/v1/regras/concorrentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConcorrentes", is(1)))
                .andExpect(jsonPath("$.temIdentica", is(true)))
                .andExpect(jsonPath("$.temEmpate", is(false)))
                .andExpect(jsonPath("$.regrasConcorrentes[0].tipoConcorrencia", is("IDENTICA")))
                .andExpect(jsonPath("$.regrasConcorrentes[0].mensagemExplicativa", containsString("é idêntica à regra")));
    }

    @Test
    @DisplayName("Deve identificar EMPATE quando os pesos de especificidade forem iguais mas com taxas diferentes")
    void deveIdentificarEmpate() throws Exception {
        Regra r = new Regra();
        r.setCampanha(campanhaExistente);
        r.setNome("Regra Loja 1 Vendedor");
        r.setCodLoja(1);
        r.setCodCargo(100);
        r.setTaxa(new BigDecimal("0.0300"));
        r.setDataInicio(LocalDate.of(2026, 10, 1));
        r.setDataFim(LocalDate.of(2026, 10, 31));
        r.setStatus(StatusRegra.ATIVA);
        regraRepository.save(r);

        // Proposta com mesmas dimensões mas taxa diferente (0.0450)
        ConsultaConcorrenciaRequest request = new ConsultaConcorrenciaRequest(
                null,
                null,
                100,
                1,
                null,
                null,
                new BigDecimal("0.0450"),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)
        );

        mockMvc.perform(post("/api/v1/regras/concorrentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConcorrentes", is(1)))
                .andExpect(jsonPath("$.temEmpate", is(true)))
                .andExpect(jsonPath("$.temIdentica", is(false)))
                .andExpect(jsonPath("$.regrasConcorrentes[0].tipoConcorrencia", is("EMPATE")))
                .andExpect(jsonPath("$.regrasConcorrentes[0].mensagemExplicativa", containsString("possui o mesmo grau de prioridade")));
    }

    @Test
    @DisplayName("Deve identificar SOBREPOSTA_MAIOR_PRECEDENCIA quando a proposta for mais específica que a existente")
    void deveIdentificarMaiorPrecedencia() throws Exception {
        // Regra existente ampla: Apenas codMarca = 10 (peso 1)
        Regra r = new Regra();
        r.setCampanha(campanhaExistente);
        r.setNome("Regra Geral Marca 10");
        r.setCodMarca(10);
        r.setDescrMarca("PRETO");
        r.setTaxa(new BigDecimal("0.0200"));
        r.setDataInicio(LocalDate.of(2026, 10, 1));
        r.setDataFim(LocalDate.of(2026, 10, 31));
        r.setStatus(StatusRegra.ATIVA);
        regraRepository.save(r);

        // Proposta específica: Marca 10 + Loja 1 + Cargo 100 (peso 1 + 8 + 4 = 13)
        ConsultaConcorrenciaRequest request = new ConsultaConcorrenciaRequest(
                null,
                10,
                100,
                1,
                null,
                null,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)
        );

        mockMvc.perform(post("/api/v1/regras/concorrentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConcorrentes", is(1)))
                .andExpect(jsonPath("$.regrasConcorrentes[0].tipoConcorrencia", is("SOBREPOSTA_MAIOR_PRECEDENCIA")))
                .andExpect(jsonPath("$.regrasConcorrentes[0].especificidadeProposta", is(13)))
                .andExpect(jsonPath("$.regrasConcorrentes[0].especificidadeConcorrente", is(1)))
                .andExpect(jsonPath("$.regrasConcorrentes[0].mensagemExplicativa", containsString("é mais específica")));
    }

    @Test
    @DisplayName("Deve identificar SOBREPOSTA_MENOR_PRECEDENCIA quando a regra existente for mais específica que a proposta")
    void deveIdentificarMenorPrecedencia() throws Exception {
        // Regra existente específica: Loja 1 + Cargo 100 (peso 8 + 4 = 12)
        Regra r = new Regra();
        r.setCampanha(campanhaExistente);
        r.setNome("Regra Específica Loja 1");
        r.setCodLoja(1);
        r.setCodCargo(100);
        r.setTaxa(new BigDecimal("0.0400"));
        r.setDataInicio(LocalDate.of(2026, 10, 1));
        r.setDataFim(LocalDate.of(2026, 10, 31));
        r.setStatus(StatusRegra.ATIVA);
        regraRepository.save(r);

        // Proposta genérica: Apenas Marca 10 (peso 1)
        ConsultaConcorrenciaRequest request = new ConsultaConcorrenciaRequest(
                null,
                10,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31)
        );

        mockMvc.perform(get("/api/v1/regras/concorrentes")
                        .param("codMarca", "10")
                        .param("dataInicio", "2026-10-01")
                        .param("dataFim", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConcorrentes", is(1)))
                .andExpect(jsonPath("$.regrasConcorrentes[0].tipoConcorrencia", is("SOBREPOSTA_MENOR_PRECEDENCIA")))
                .andExpect(jsonPath("$.regrasConcorrentes[0].especificidadeProposta", is(1)))
                .andExpect(jsonPath("$.regrasConcorrentes[0].especificidadeConcorrente", is(12)))
                .andExpect(jsonPath("$.regrasConcorrentes[0].mensagemExplicativa", containsString("é mais específica")));
    }
}
