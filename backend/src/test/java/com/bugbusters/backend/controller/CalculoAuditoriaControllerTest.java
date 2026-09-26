package com.bugbusters.backend.controller;

import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.repository.LogCalculoRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class CalculoAuditoriaControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private LogCalculoRepository logCalculoRepository;

    private MockMvc mockMvc;

    private UUID logId1;
    private UUID logId2;
    private UUID logId3;
    private UUID vendaId1;
    private UUID vendaId2;
    private UUID vendaId3;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        logCalculoRepository.deleteAll();

        vendaId1 = UUID.randomUUID();
        vendaId2 = UUID.randomUUID();
        vendaId3 = UUID.randomUUID();

        // Log 1: MATRIC-10, Regra 1, 2025-11-10, ECOMMERCE
        LogCalculoImutavel log1 = new LogCalculoImutavel(
                UUID.randomUUID(),
                vendaId1,
                "MATRIC-10",
                100,
                75,
                10,
                new BigDecimal("1000.00"),
                new BigDecimal("0.0250"),
                new BigDecimal("25.00"),
                1L,
                LocalDate.of(2025, 11, 10),
                "ECOMMERCE",
                "MOTOR_PRODUCAO"
        );
        log1 = logCalculoRepository.save(log1);
        logId1 = log1.getId();

        // Log 2: MATRIC-20, Regra 2, 2025-11-20, LOJA_FISICA
        LogCalculoImutavel log2 = new LogCalculoImutavel(
                UUID.randomUUID(),
                vendaId2,
                "MATRIC-20",
                200,
                30,
                20,
                new BigDecimal("2000.00"),
                new BigDecimal("0.0300"),
                new BigDecimal("60.00"),
                2L,
                LocalDate.of(2025, 11, 20),
                "LOJA_FISICA",
                "MOTOR_PRODUCAO"
        );
        log2 = logCalculoRepository.save(log2);
        logId2 = log2.getId();

        // Log 3: MATRIC-10, Regra 1, 2025-12-05, ECOMMERCE
        LogCalculoImutavel log3 = new LogCalculoImutavel(
                UUID.randomUUID(),
                vendaId3,
                "MATRIC-10",
                100,
                75,
                10,
                new BigDecimal("3000.00"),
                new BigDecimal("0.0250"),
                new BigDecimal("75.00"),
                1L,
                LocalDate.of(2025, 12, 5),
                "ECOMMERCE",
                "MOTOR_PRODUCAO"
        );
        log3 = logCalculoRepository.save(log3);
        logId3 = log3.getId();
    }

    @Test
    @DisplayName("BUG-24.1: GET /api/v1/logs-calculo - Deve retornar listagem paginada padrão com metadados")
    void deveListarLogsPaginadosPadrao() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.content[0].idLog").isNotEmpty())
                .andExpect(jsonPath("$.content[0].matricula").isNotEmpty())
                .andExpect(jsonPath("$.content[0].valorVenda").isNumber())
                .andExpect(jsonPath("$.content[0].valorComissao").isNumber());
    }

    @Test
    @DisplayName("BUG-24.2: GET /api/v1/logs-calculo?page=0&size=2 - Deve respeitar paginação customizada")
    void deveListarLogsComPaginacaoCustomizada() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo")
                .param("page", "0")
                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @DisplayName("BUG-24.3: GET /api/v1/logs-calculo?idVenda={uuid} - Deve filtrar logs por identificador de venda")
    void deveFiltrarLogsPorIdVenda() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo")
                .param("idVenda", vendaId1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].idVenda").value(vendaId1.toString()))
                .andExpect(jsonPath("$.content[0].matricula").value("MATRIC-10"))
                .andExpect(jsonPath("$.content[0].valorVenda").value(1000.00));
    }

    @Test
    @DisplayName("BUG-24.4: GET /api/v1/logs-calculo?matricula={matricula} - Deve filtrar logs por colaborador da venda")
    void deveFiltrarLogsPorMatricula() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo")
                .param("matricula", "matric-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].matricula").value("MATRIC-10"))
                .andExpect(jsonPath("$.content[1].matricula").value("MATRIC-10"));
    }

    @Test
    @DisplayName("BUG-24.5: GET /api/v1/logs-calculo?idRegra={idRegra} - Deve filtrar logs por regra")
    void deveFiltrarLogsPorRegra() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo")
                .param("idRegra", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].idRegra").value(2))
                .andExpect(jsonPath("$.content[0].matricula").value("MATRIC-20"))
                .andExpect(jsonPath("$.content[0].taxaAplicada").value(0.0300));
    }

    @Test
    @DisplayName("BUG-24.6: GET /api/v1/logs-calculo?dataInicio={d1}&dataFim={d2} - Deve filtrar logs por período")
    void deveFiltrarLogsPorPeriodo() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo")
                .param("dataInicio", "2025-11-01")
                .param("dataFim", "2025-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].dataVenda", anyOf(equalTo("2025-11-10"), equalTo("2025-11-20"))))
                .andExpect(jsonPath("$.content[1].dataVenda", anyOf(equalTo("2025-11-10"), equalTo("2025-11-20"))));
    }

    @Test
    @DisplayName("BUG-24.7: GET /api/v1/logs-calculo com período incoerente - Deve retornar 400 Bad Request")
    void deveRetornarBadRequestParaPeriodoIncoerente() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo")
                .param("dataInicio", "2025-12-31")
                .param("dataFim", "2025-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Data de início não pode ser posterior à data de término.")));
    }

    @Test
    @DisplayName("BUG-24.8: GET /api/v1/logs-calculo com ausência de registros - Deve retornar 200 OK com página vazia")
    void deveRetornarPaginaVaziaQuandoNaoHouverRegistros() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo")
                .param("matricula", "MATRIC-INEXISTENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("BUG-24.9: GET /api/v1/logs-calculo/{id} - Deve retornar detalhe por identificador com valores históricos preservados")
    void deveRetornarDetalhePorIdentificadorExistente() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo/" + logId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLog").value(logId1.toString()))
                .andExpect(jsonPath("$.protocolo").isNotEmpty())
                .andExpect(jsonPath("$.idVenda").value(vendaId1.toString()))
                .andExpect(jsonPath("$.matricula").value("MATRIC-10"))
                .andExpect(jsonPath("$.codCargo").value(100))
                .andExpect(jsonPath("$.codLoja").value(75))
                .andExpect(jsonPath("$.codMarca").value(10))
                .andExpect(jsonPath("$.valorOriginal").value(1000.00))
                .andExpect(jsonPath("$.valorVenda").value(1000.00))
                .andExpect(jsonPath("$.taxaAplicada").value(0.0250))
                .andExpect(jsonPath("$.valorComissao").value(25.00))
                .andExpect(jsonPath("$.idRegra").value(1))
                .andExpect(jsonPath("$.dataVenda").value("2025-11-10"))
                .andExpect(jsonPath("$.canal").value("ECOMMERCE"))
                .andExpect(jsonPath("$.origemExecucao").value("MOTOR_PRODUCAO"))
                .andExpect(jsonPath("$.usuarioExecutor").value("SISTEMA"))
                .andExpect(jsonPath("$.executadoEm").isNotEmpty());
    }

    @Test
    @DisplayName("BUG-24.10: GET /api/v1/logs-calculo/{id} com identificador inexistente - Deve retornar 404 Not Found")
    void deveRetornarNotFoundParaIdentificadorInexistente() throws Exception {
        UUID idInexistente = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/logs-calculo/" + idInexistente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString(idInexistente.toString())));
    }

    @Test
    @DisplayName("BUG-24.11: PUT e DELETE /api/v1/logs-calculo/{id} - Deve proibir alteração e exclusão (405 Method Not Allowed - Somente Leitura)")
    void deveGarantirApiSomenteLeituraSemAlteracaoOuExclusao() throws Exception {
        mockMvc.perform(put("/api/v1/logs-calculo/" + logId1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/v1/logs-calculo/" + logId1))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(patch("/api/v1/logs-calculo/" + logId1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
