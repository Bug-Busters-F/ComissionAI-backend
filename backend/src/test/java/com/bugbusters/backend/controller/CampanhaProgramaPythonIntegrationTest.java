package com.bugbusters.backend.controller;

import com.bugbusters.backend.dto.campanha.CampanhaRequest;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.programapython.AprovarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.AtualizarProgramaPythonRequest;
import com.bugbusters.backend.dto.programapython.RejeitarProgramaPythonRequest;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.CampanhaProgramaPython;
import com.bugbusters.backend.model.EstadoCampanha;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampanhaProgramaPythonIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private Long criarCampanhaTeste(String titulo) throws Exception {
        RegraItemRequest regra1 = new RegraItemRequest(
                "bloco-1",
                "Comissão de 5% no canal ecommerce",
                "Regra E-commerce 5%",
                "ECOMMERCE",
                10, "PRETO", null, null, null, null,
                null, null, null, null,
                TipoOperacaoBase.DEFINIR_TAXA,
                new BigDecimal("0.0500"),
                null, null, null,
                new BigDecimal("0.0500"),
                "Aplica 5% no ecommerce para marca PRETO",
                "def calcular_comissao(venda): return 0.05",
                List.of(),
                true,
                StatusRegra.DRAFT,
                null, null
        );

        CampanhaRequest request = new CampanhaRequest(
                titulo,
                "Texto comercial da campanha",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                EstadoCampanha.DRAFT,
                List.of(regra1),
                null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/campanhas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    @Test
    @DisplayName("Deve gerar programa Python automaticamente com SHA-256 e status PENDENTE_REVISAO no primeiro acesso")
    void deveObterOuGerarProgramaPythonAutomaticamente() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Auto Gera Python");

        mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campanhaId", is(campanhaId.intValue())))
                .andExpect(jsonPath("$.versaoPrograma", is(1)))
                .andExpect(jsonPath("$.statusAprovacao", is("PENDENTE_REVISAO")))
                .andExpect(jsonPath("$.checksumSha256", notNullValue()))
                .andExpect(jsonPath("$.codigoFonte", containsString("def calcular_comissao(venda: dict)")))
                .andExpect(jsonPath("$.codigoFonte", containsString("ECOMMERCE")))
                .andExpect(jsonPath("$.validoParaExecucao", is(false)));
    }

    @Test
    @DisplayName("Deve atualizar código-fonte do programa Python, incrementando versão e recalculando hash")
    void deveAtualizarCodigoFonteEIncrementarVersao() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Atualizar Código");

        String novoCodigo = """
                def calcular_comissao(venda: dict) -> float | None:
                    valor = float(venda.get('valor_venda', 0))
                    if valor > 1000.0:
                        return valor * 0.07
                    return valor * 0.05
                """;
        String checksumEsperado = CampanhaProgramaPython.calcularChecksum(novoCodigo.trim());

        AtualizarProgramaPythonRequest request = new AtualizarProgramaPythonRequest(
                novoCodigo,
                "Revisor de Teste",
                "Ajuste da taxa para faixas superiores"
        );

        mockMvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoPrograma", is(1)))
                .andExpect(jsonPath("$.checksumSha256", is(checksumEsperado)))
                .andExpect(jsonPath("$.statusAprovacao", is("PENDENTE_REVISAO")));

        // Atualizar novamente com código diferente gera versão 2
        String codigoV2 = novoCodigo + "\n# versao 2";
        AtualizarProgramaPythonRequest requestV2 = new AtualizarProgramaPythonRequest(
                codigoV2,
                "Revisor 2",
                "Adicionado comentário de versão"
        );

        mockMvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestV2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoPrograma", is(2)))
                .andExpect(jsonPath("$.statusAprovacao", is("PENDENTE_REVISAO")));
    }

    @Test
    @DisplayName("Deve aprovar formalmente o programa Python e avançar a esteira para simulação")
    void deveAprovarProgramaPythonEAvancarEsteira() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Aprovar Programa");

        // Obter primeiro o programa gerado e seu checksum
        MvcResult getResult = mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId))
                .andExpect(status().isOk())
                .andReturn();
        String checksum = objectMapper.readTree(getResult.getResponse().getContentAsString()).get("checksumSha256").asText();

        AprovarProgramaPythonRequest aprovarRequest = new AprovarProgramaPythonRequest(
                "Auditor Tecnico",
                checksum,
                "Código auditado e aprovado com sucesso."
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aprovarRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacao", is("APROVADO")))
                .andExpect(jsonPath("$.aprovadoPor", is("Auditor Tecnico")))
                .andExpect(jsonPath("$.aprovadoEm", notNullValue()))
                .andExpect(jsonPath("$.validoParaExecucao", is(true)));

        // Verifica se a campanha avançou a esteira (etapaMaisAvancada >= 3)
        mockMvc.perform(get("/api/v1/campanhas/{id}", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaMaisAvancada", greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("Deve rejeitar aprovação se o checksum informado pelo revisor for diferente do código atual no banco")
    void deveRejeitarAprovacaoSeChecksumConcorrenteDivergir() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Concorrência Hash");

        AprovarProgramaPythonRequest aprovarRequest = new AprovarProgramaPythonRequest(
                "Auditor Tecnico",
                "hash_obsoleto_ou_incorreto_1234567890abcdef",
                "Tentativa de aprovação com hash antigo."
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/aprovar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aprovarRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Divergência de integridade")));
    }

    @Test
    @DisplayName("Deve rejeitar programa Python na revisão técnica com justificativa")
    void deveRejeitarProgramaPythonComJustificativa() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Rejeitar Programa");

        RejeitarProgramaPythonRequest rejeitarRequest = new RejeitarProgramaPythonRequest(
                "Auditor de Compliance",
                "Regra de faixa mínima incorreta, deve retornar None para valores zerados."
        );

        mockMvc.perform(post("/api/v1/campanhas/{id}/programa-python/rejeitar", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejeitarRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusAprovacao", is("REJEITADO")))
                .andExpect(jsonPath("$.rejeitadoPor", is("Auditor de Compliance")))
                .andExpect(jsonPath("$.rejeitadoEm", notNullValue()))
                .andExpect(jsonPath("$.justificativa", containsString("faixa mínima incorreta")))
                .andExpect(jsonPath("$.validoParaExecucao", is(false)));
    }

    @Test
    @DisplayName("Deve listar o histórico de versões do programa Python da campanha")
    void deveListarHistoricoDoProgramaPython() throws Exception {
        Long campanhaId = criarCampanhaTeste("Campanha Histórico Programa");

        // Cria versão 1 (gerada)
        mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python", campanhaId))
                .andExpect(status().isOk());

        // Salva versão 2
        mockMvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtualizarProgramaPythonRequest(
                                "def calcular_comissao(venda):\n    return 0.10", "Dev", "Versão 2"))))
                .andExpect(status().isOk());

        // Salva versão 3
        mockMvc.perform(put("/api/v1/campanhas/{id}/programa-python", campanhaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AtualizarProgramaPythonRequest(
                                "def calcular_comissao(venda):\n    return 0.12", "Dev", "Versão 3"))))
                .andExpect(status().isOk());

        // Consulta histórico
        mockMvc.perform(get("/api/v1/campanhas/{id}/programa-python/historico", campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].versaoPrograma", is(3)))
                .andExpect(jsonPath("$[1].versaoPrograma", is(2)))
                .andExpect(jsonPath("$[2].versaoPrograma", is(1)));
    }
}

