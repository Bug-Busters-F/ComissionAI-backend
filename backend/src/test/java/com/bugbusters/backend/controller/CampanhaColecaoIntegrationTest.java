package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.campanha.AlterarEstadoCampanhaRequest;
import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.EstadoCampanha;
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
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class CampanhaColecaoIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("Deve criar campanha com coleção de múltiplas regras e sincronizar vigência")
    void deveCriarCampanhaComColecaoDeMultiplasRegras() throws Exception {
        RegraItemRequest regra1 = new RegraItemRequest(
                "bloco-1",
                "Comissão de 5% no canal ecommerce",
                "Regra E-commerce Geral",
                "ECOMMERCE",
                null, null, null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA,
                new BigDecimal("0.0500"),
                null, null, null,
                new BigDecimal("0.0500"),
                "Aplica 5% no ecommerce",
                "def calcular_comissao(venda): ...",
                List.of(),
                true,
                StatusRegra.DRAFT
        );

        RegraItemRequest regra2 = new RegraItemRequest(
                "bloco-2",
                "Para a loja 75, taxa base + 1.5% em vendas acima de R$ 5.000",
                "Regra Loja 75 com Faixa",
                null,
                null, null,
                75,
                null, null, null,
                new BigDecimal("5000.00"),
                false,
                null,
                null,
                TipoOperacaoBase.ACRESCIMO_PONTOS,
                new BigDecimal("0.0150"),
                "BASE_COMISS",
                new BigDecimal("0.0300"),
                "Taxa Contratual Padrão",
                new BigDecimal("0.0450"),
                "Aplica 4.5% na loja 75",
                "def calcular_comissao(venda): ...",
                List.of(),
                true,
                StatusRegra.DRAFT
        );

        CampanhaRequest request = new CampanhaRequest(
                "Campanha Multi-Regras Outubro 2026",
                "1. E-commerce 5%. 2. Loja 75 acima de 5000 base + 1.5%",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                EstadoCampanha.DRAFT,
                List.of(regra1, regra2)
        );

        mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.titulo", is("Campanha Multi-Regras Outubro 2026")))
                .andExpect(jsonPath("$.estado", is("DRAFT")))
                .andExpect(jsonPath("$.dataInicio", is("2026-10-01")))
                .andExpect(jsonPath("$.dataFim", is("2026-10-31")))
                .andExpect(jsonPath("$.regras", hasSize(2)))
                .andExpect(jsonPath("$.regras[0].blocoId", is("bloco-1")))
                .andExpect(jsonPath("$.regras[0].canal", is("ECOMMERCE")))
                .andExpect(jsonPath("$.regras[0].taxa", is(0.0500)))
                .andExpect(jsonPath("$.regras[0].dataInicio", is("2026-10-01")))
                .andExpect(jsonPath("$.regras[0].dataFim", is("2026-10-31")))
                .andExpect(jsonPath("$.regras[1].blocoId", is("bloco-2")))
                .andExpect(jsonPath("$.regras[1].codLoja", is(75)))
                .andExpect(jsonPath("$.regras[1].valorMinimo", is(5000.00)))
                .andExpect(jsonPath("$.regras[1].tipoOperacao", is("ACRESCIMO_PONTOS")))
                .andExpect(jsonPath("$.regras[1].taxa", is(0.0450)))
                .andExpect(jsonPath("$.regras[1].dataInicio", is("2026-10-01")))
                .andExpect(jsonPath("$.regras[1].dataFim", is("2026-10-31")))
                .andExpect(jsonPath("$.possuiIncompletas", is(false)));
    }

    @Test
    @DisplayName("Deve permitir salvar propostas incompletas como rascunho com taxa nula")
    void deveSalvarPropostaIncompletaComoRascunho() throws Exception {
        RegraItemRequest regraIncompleta = new RegraItemRequest(
                "bloco-rascunho",
                "Regra ainda em definição para loja 10",
                "Rascunho Loja 10",
                null,
                null, null,
                10,
                null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA,
                null,
                null, null, null,
                null, // taxa nula!
                null,
                null,
                List.of("Taxa não definida pelo usuário", "Pendente validação de cargo"),
                false,
                StatusRegra.DRAFT
        );

        CampanhaRequest request = new CampanhaRequest(
                "Campanha com Proposta Incompleta",
                "Loja 10 com condições a definir",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                EstadoCampanha.DRAFT,
                List.of(regraIncompleta)
        );

        String responseJson = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("DRAFT")))
                .andExpect(jsonPath("$.possuiIncompletas", is(true)))
                .andExpect(jsonPath("$.regras[0].status", is("DRAFT")))
                .andExpect(jsonPath("$.regras[0].completa", is(false)))
                .andExpect(jsonPath("$.regras[0].taxa", nullValue()))
                .andExpect(jsonPath("$.regras[0].pendencias", containsString("Taxa não definida")))
                .andReturn().getResponse().getContentAsString();

        Number campanhaId = objectMapper.readTree(responseJson).get("id").numberValue();

        // Tentativa de ativar a campanha contendo proposta incompleta deve ser bloqueada (400)
        AlterarEstadoCampanhaRequest requestAtivacao = new AlterarEstadoCampanhaRequest(EstadoCampanha.ATIVA);
        mockMvc.perform(patch("/api/v1/campanhas/" + campanhaId + "/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestAtivacao)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Não é permitido ativar campanha contendo regras ou propostas incompletas")));
    }

    @Test
    @DisplayName("Deve rejeitar criação com estado ATIVA caso haja regras incompletas")
    void deveRejeitarCriacaoAtivaComRegrasIncompletas() throws Exception {
        RegraItemRequest regraIncompleta = new RegraItemRequest(
                "bloco-incompleto",
                "Texto incompleto",
                "Regra Sem Taxa",
                null, null, null, null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA,
                null, null, null, null,
                null, null, null,
                List.of("Pendência"),
                false,
                StatusRegra.DRAFT
        );

        CampanhaRequest request = new CampanhaRequest(
                "Tentativa Campanha Ativa com Incompletas",
                "Texto",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                EstadoCampanha.ATIVA, // Não permitido com regras incompletas
                List.of(regraIncompleta)
        );

        mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Campanha contendo regras ou propostas incompletas só pode ser salva como rascunho")));
    }

    @Test
    @DisplayName("Deve atualizar campanha e coleção de regras transacionalmente")
    void deveAtualizarColecaoDeRegrasTransacionalmente() throws Exception {
        // 1. Cria campanha inicial com 1 regra
        RegraItemRequest inicial = new RegraItemRequest(
                "bloco-init", "Texto", "Regra Inicial", "ECOMMERCE",
                null, null, null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0300"),
                null, null, null, new BigDecimal("0.0300"),
                null, null, List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest requestCriacao = new CampanhaRequest(
                "Campanha Para Atualizar", "Texto Original",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                EstadoCampanha.DRAFT, List.of(inicial)
        );

        String createJson = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestCriacao)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(createJson).get("id").asLong();

        // 2. Atualiza para 2 regras com vigência nova
        RegraItemRequest nova1 = new RegraItemRequest(
                "b1", "E-com", "R1", "ECOMMERCE",
                null, null, null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0400"),
                null, null, null, new BigDecimal("0.0400"),
                null, null, List.of(), true, StatusRegra.DRAFT
        );
        RegraItemRequest nova2 = new RegraItemRequest(
                "b2", "Loja 50", "R2", null,
                null, null, 50, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA, new BigDecimal("0.0600"),
                null, null, null, new BigDecimal("0.0600"),
                null, null, List.of(), true, StatusRegra.DRAFT
        );

        CampanhaRequest requestUpdate = new CampanhaRequest(
                "Campanha Atualizada", "Texto Atualizado",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                EstadoCampanha.DRAFT, List.of(nova1, nova2)
        );

        mockMvc.perform(put("/api/v1/campanhas/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", is("Campanha Atualizada")))
                .andExpect(jsonPath("$.dataInicio", is("2026-11-01")))
                .andExpect(jsonPath("$.dataFim", is("2026-11-30")))
                .andExpect(jsonPath("$.regras", hasSize(2)))
                .andExpect(jsonPath("$.regras[0].dataInicio", is("2026-11-01")))
                .andExpect(jsonPath("$.regras[0].dataFim", is("2026-11-30")))
                .andExpect(jsonPath("$.regras[1].dataInicio", is("2026-11-01")))
                .andExpect(jsonPath("$.regras[1].dataFim", is("2026-11-30")));
    }
}
