package com.bugbusters.backend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class ControllerRoutesTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private org.springframework.web.client.RestClient.Builder aiRestClientBuilder;

    @Autowired
    private com.bugbusters.backend.service.client.AiServiceClient aiServiceClient;

    private org.springframework.test.web.client.MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        mockServer = org.springframework.test.web.client.MockRestServiceServer.bindTo(aiRestClientBuilder).build();
        aiServiceClient.setAiRestClient(aiRestClientBuilder.build());
    }

    // ==========================================
    // 1. Regras Controller
    // ==========================================
    @Test
    @DisplayName("POST /api/v1/regras - Deve criar regra válida com sucesso (201)")
    void deveCriarRegraValida() throws Exception {
        String payload = """
            {
                "nome": "Comissão Black Friday",
                "canal": "ECOMMERCE",
                "taxa": 0.0500,
                "dataInicio": "2026-11-01",
                "dataFim": "2026-11-30"
            }
            """;

        mockMvc.perform(post("/api/v1/regras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Comissão Black Friday"))
                .andExpect(jsonPath("$.canal").value("ECOMMERCE"))
                .andExpect(jsonPath("$.taxa").value(0.0500))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.dataFim").value("2026-11-30"));
    }

    @Test
    @DisplayName("POST /api/v1/regras - Deve calcular dataFim (+30 dias) quando omitida")
    void deveCalcularDataFimQuandoOmitida() throws Exception {
        String payload = """
            {
                "nome": "Regra Sem Fim",
                "canal": "LOJA_FISICA",
                "taxa": 0.0800,
                "dataInicio": "2026-10-01"
            }
            """;

        mockMvc.perform(post("/api/v1/regras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.dataFim").value("2026-10-31"));
    }

    @Test
    @DisplayName("POST /api/v1/regras - Deve criar regra válida com dimensões de público-alvo e sem canal")
    void deveCriarRegraComDimensoesPublicoAlvoSemCanal() throws Exception {
        String payload = """
            {
                "nome": "Comissão Gerentes Marca Preto",
                "codMarca": 10,
                "codLoja": 75,
                "codCargo": 150,
                "descriCargo": "GERENTE DE LOJA",
                "matricula": "MATRIC-888",
                "taxa": 0.0100,
                "dataInicio": "2026-11-01"
            }
            """;

        mockMvc.perform(post("/api/v1/regras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Comissão Gerentes Marca Preto"))
                .andExpect(jsonPath("$.canal").doesNotExist())
                .andExpect(jsonPath("$.codMarca").value(10))
                .andExpect(jsonPath("$.codLoja").value(75))
                .andExpect(jsonPath("$.codCargo").value(150))
                .andExpect(jsonPath("$.descriCargo").value("GERENTE DE LOJA"))
                .andExpect(jsonPath("$.matricula").value("MATRIC-888"))
                .andExpect(jsonPath("$.taxa").value(0.0100))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.dataFim").value("2026-12-01"));
    }

    @Test
    @DisplayName("POST /api/v1/regras - Deve padronizar empresa/marca case-insensitively (Vermelho e vermelho -> VERMELHO)")
    void devePadronizarNomeEmpresaCaseInsensitive() throws Exception {
        // Teste 1: Enviando 'Vermelho' com inicial maiúscula
        String payload1 = """
            {
                "nome": "Regra Marca Vermelho Maiúscula",
                "descrMarca": "Vermelho",
                "taxa": 0.0500,
                "dataInicio": "2026-11-01"
            }
            """;

        mockMvc.perform(post("/api/v1/regras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descrMarca").value("VERMELHO"))
                .andExpect(jsonPath("$.codMarca").value(40));

        // Teste 2: Enviando 'vermelho' em minúsculas
        String payload2 = """
            {
                "nome": "Regra Marca vermelho Minúscula",
                "descrMarca": "vermelho",
                "taxa": 0.0500,
                "dataInicio": "2026-11-01"
            }
            """;

        mockMvc.perform(post("/api/v1/regras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descrMarca").value("VERMELHO"))
                .andExpect(jsonPath("$.codMarca").value(40));
    }

    @Test
    @DisplayName("POST /api/v1/regras - Deve permitir e padronizar nova cor de empresa sem bloquear")
    void devePermitirNovaCorDeEmpresa() throws Exception {
        String payload = """
            {
                "nome": "Regra Nova Empresa Roxo",
                "descrMarca": "  roxo  ",
                "taxa": 0.0350,
                "dataInicio": "2026-11-01"
            }
            """;

        mockMvc.perform(post("/api/v1/regras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descrMarca").value("ROXO"));
    }

    @Test
    @DisplayName("POST /api/v1/regras - Deve rejeitar payload inválido com 400 e lista de validações")
    void deveRejeitarRegraInvalida() throws Exception {
        String payload = """
            {
                "nome": "",
                "taxa": -0.05
            }
            """;

        mockMvc.perform(post("/api/v1/regras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Dados de entrada inválidos."))
                .andExpect(jsonPath("$.validacoes", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("GET /api/v1/regras - Deve listar regras com 200 OK")
    void deveListarRegras() throws Exception {
        mockMvc.perform(get("/api/v1/regras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].canal").value("ECOMMERCE"));
    }

    @Test
    @DisplayName("GET /api/v1/regras/{id} - Deve buscar regra por ID com 200 OK")
    void deveBuscarRegraPorId() throws Exception {
        mockMvc.perform(get("/api/v1/regras/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ATIVA"));
    }

    @Test
    @DisplayName("DELETE /api/v1/regras/{id} - Deve desativar regra com 204 No Content")
    void deveDesativarRegra() throws Exception {
        mockMvc.perform(delete("/api/v1/regras/1"))
                .andExpect(status().isNoContent());
    }

    // ==========================================
    // 2. Calculo Controller
    // ==========================================
    @Test
    @DisplayName("POST /api/v1/comissoes/calcular - Deve processar cálculo com 200 OK")
    void deveCalcularComissao() throws Exception {
        String payload = """
            {
                "matricula": "MATRIC-1234",
                "valorVenda": 1000.00,
                "canal": "ECOMMERCE",
                "dataVenda": "2026-10-05"
            }
            """;

        mockMvc.perform(post("/api/v1/comissoes/calcular")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocoloCalculo").isNotEmpty())
                .andExpect(jsonPath("$.matricula").value("MATRIC-1234"))
                .andExpect(jsonPath("$.valorOriginal").value(1000.00))
                .andExpect(jsonPath("$.valorComissao").value(100.00))
                .andExpect(jsonPath("$.dataCalculo").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/comissoes/calcular - Deve retornar resultado idêntico sem duplicar logs em caso de reenvio (Idempotência)")
    void deveRetornarMesmoResultadoEmReenvioIdentico() throws Exception {
        String payload = """
            {
                "matricula": "MATRIC-IDEMPOTENTE",
                "valorVenda": 2000.00,
                "canal": "LOJA_FISICA",
                "dataVenda": "2026-10-10"
            }
            """;

        // 1ª chamada: novo cálculo
        String respostaOriginal = mockMvc.perform(post("/api/v1/comissoes/calcular")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("MATRIC-IDEMPOTENTE"))
                .andExpect(jsonPath("$.valorComissao").value(200.00))
                .andReturn().getResponse().getContentAsString();

        String protocoloOriginal = respostaOriginal.replaceAll(".*\"protocoloCalculo\":\\s*\"([^\"]+)\".*", "$1");

        // 2ª chamada idêntica: deve retornar o mesmo protocolo e mesmos dados
        mockMvc.perform(post("/api/v1/comissoes/calcular")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocoloCalculo").value(protocoloOriginal))
                .andExpect(jsonPath("$.matricula").value("MATRIC-IDEMPOTENTE"))
                .andExpect(jsonPath("$.valorComissao").value(200.00));
    }

    @Test
    @DisplayName("POST /api/v1/comissoes/calcular - Deve rejeitar reenvio com dados divergentes com 400 Bad Request")
    void deveRejeitarReenvioComDadosDivergentes() throws Exception {
        String payloadOriginal = """
            {
                "matricula": "MATRIC-CONFLITO",
                "valorVenda": 500.00,
                "canal": "APP",
                "dataVenda": "2026-10-12"
            }
            """;

        String payloadDivergente = """
            {
                "matricula": "MATRIC-CONFLITO",
                "valorVenda": 750.00,
                "canal": "APP",
                "dataVenda": "2026-10-12"
            }
            """;

        // 1ª chamada bem-sucedida
        mockMvc.perform(post("/api/v1/comissoes/calcular")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadOriginal))
                .andExpect(status().isOk());

        // 2ª chamada com valor alterado para a mesma matrícula e data de venda -> Rejeitada
        mockMvc.perform(post("/api/v1/comissoes/calcular")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadDivergente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("dados divergentes")));
    }

    @Test
    @DisplayName("GET /api/v1/logs-calculo - Deve listar logs com 200 OK")
    void deveListarLogs() throws Exception {
        mockMvc.perform(get("/api/v1/logs-calculo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].matricula").isNotEmpty());
    }

    // ==========================================
    // 3. Interpretador Controller
    // ==========================================
    @Test
    @DisplayName("POST /api/v1/interpretador/extrair-regra - Deve extrair parâmetros com 200 OK")
    void deveInterpretarRegra() throws Exception {
        String respostaSimuladaPython = """
            {
              "canal": "ecommerce",
              "taxa": 0.0500,
              "dataInicio": "2026-12-01",
              "dataFim": "2026-12-31",
              "confianca": 0.98,
              "pendencias": []
            }
            """;

        mockServer.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://localhost:8000/api/v1/interpretar"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(respostaSimuladaPython, MediaType.APPLICATION_JSON));

        String payload = """
            {
                "texto": "comissão de 5% no ecommerce para dezembro",
                "contexto": {}
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("ECOMMERCE"))
                .andExpect(jsonPath("$.taxa").value(0.05))
                .andExpect(jsonPath("$.dataInicio").value("2026-12-01"))
                .andExpect(jsonPath("$.dataFim").value("2026-12-31"))
                .andExpect(jsonPath("$.confianca").value(0.98));
    }

    @Test
    @DisplayName("POST /api/v1/interpretador/extrair-regra - Deve rejeitar texto vazio com 400")
    void deveRejeitarTextoLivreVazio() throws Exception {
        String payload = """
            {
                "texto": "   "
            }
            """;

        mockMvc.perform(post("/api/v1/interpretador/extrair-regra")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validacoes[0].campo").value("texto"));
    }

    // ==========================================
    // 4. Importacao Controller
    // ==========================================
    @Test
    @DisplayName("POST /api/v1/imports/upload - Deve realizar upload multipart com 200 OK")
    void deveRealizarUploadMultipart() throws Exception {
        byte[] excelBytes;
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            wb.createSheet("Vendas");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            excelBytes = out.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "vendas_outubro.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/api/v1/imports/upload")
                .file(file)
                .param("importType", "SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeArquivo").value("vendas_outubro.xlsx"))
                .andExpect(jsonPath("$.tipoBase").value("SALES"));
    }

    // ==========================================
    // 5. Campanha Controller
    // ==========================================
    @Test
    @DisplayName("POST /api/v1/campanhas - Deve cadastrar campanha com sucesso (201) e regra em DRAFT")
    void deveCriarCampanhaValida() throws Exception {
        String payload = """
            {
                "titulo": "Campanha Black Friday 2026",
                "textoOriginal": "Comissão de 5% para vendas no e-commerce em novembro",
                "canal": "ECOMMERCE",
                "taxa": 0.0500,
                "dataInicio": "2026-11-01",
                "dataFim": "2026-11-30"
            }
            """;

        mockMvc.perform(post("/api/v1/campanhas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.titulo").value("Campanha Black Friday 2026"))
                .andExpect(jsonPath("$.estado").value("DRAFT"))
                .andExpect(jsonPath("$.regra.canal").value("ECOMMERCE"))
                .andExpect(jsonPath("$.regra.taxa").value(0.0500))
                .andExpect(jsonPath("$.regra.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /api/v1/campanhas - Deve cadastrar campanha com dimensões de público-alvo e sem canal")
    void deveCriarCampanhaComDimensoesPublicoAlvoSemCanal() throws Exception {
        String payload = """
            {
                "titulo": "Campanha Gerente Quiosque Marca Azul",
                "textoOriginal": "Comissão de 0.75% para gerente quiosque da marca azul loja 30",
                "codMarca": 30,
                "codLoja": 30,
                "codCargo": 150,
                "descriCargo": "GERENTE QUIOSQUE",
                "matricula": "MATRIC-999",
                "taxa": 0.0075,
                "dataInicio": "2026-11-01",
                "dataFim": "2026-11-30"
            }
            """;

        mockMvc.perform(post("/api/v1/campanhas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Campanha Gerente Quiosque Marca Azul"))
                .andExpect(jsonPath("$.regra.canal").doesNotExist())
                .andExpect(jsonPath("$.regra.codMarca").value(30))
                .andExpect(jsonPath("$.regra.codLoja").value(30))
                .andExpect(jsonPath("$.regra.codCargo").value(150))
                .andExpect(jsonPath("$.regra.descriCargo").value("GERENTE QUIOSQUE"))
                .andExpect(jsonPath("$.regra.matricula").value("MATRIC-999"))
                .andExpect(jsonPath("$.regra.taxa").value(0.0075));
    }

    @Test
    @DisplayName("POST /api/v1/campanhas - Deve padronizar empresa case-insensitively ao criar campanha")
    void devePadronizarEmpresaEmCampanhaCaseInsensitive() throws Exception {
        String payload = """
            {
                "titulo": "Campanha Empresa Vermelho",
                "textoOriginal": "Comissão de 4% para a empresa vermelho",
                "descrMarca": "  veRmelho  ",
                "taxa": 0.0400,
                "dataInicio": "2026-11-01"
            }
            """;

        mockMvc.perform(post("/api/v1/campanhas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regra.descrMarca").value("VERMELHO"))
                .andExpect(jsonPath("$.regra.codMarca").value(40));
    }

    @Test
    @DisplayName("POST /api/v1/campanhas - Deve calcular dataFim (+30 dias) quando omitida")
    void deveCalcularDataFimCampanhaQuandoOmitida() throws Exception {
        String payload = """
            {
                "titulo": "Campanha Sem Fim",
                "textoOriginal": "Comissão de 6% no varejo físico",
                "canal": "LOJA_FISICA",
                "taxa": 0.0600,
                "dataInicio": "2026-10-01"
            }
            """;

        mockMvc.perform(post("/api/v1/campanhas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.dataFim").value("2026-10-31"));
    }

    @Test
    @DisplayName("POST /api/v1/campanhas - Deve rejeitar dados inválidos com 400 Bad Request")
    void deveRejeitarCampanhaInvalida() throws Exception {
        String payload = """
            {
                "titulo": "",
                "textoOriginal": "",
                "canal": "",
                "taxa": -0.05
            }
            """;

        mockMvc.perform(post("/api/v1/campanhas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validacoes", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("POST /api/v1/campanhas - Deve rejeitar período incoerente com 400 Bad Request")
    void deveRejeitarPeriodoIncoerente() throws Exception {
        String payload = """
            {
                "titulo": "Campanha Datas Invertidas",
                "textoOriginal": "Texto da regra",
                "canal": "ECOMMERCE",
                "taxa": 0.0500,
                "dataInicio": "2026-12-01",
                "dataFim": "2026-11-01"
            }
            """;

        mockMvc.perform(post("/api/v1/campanhas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Período incoerente")));
    }

    @Test
    @DisplayName("GET, PUT e DELETE /api/v1/campanhas - Ciclo completo de vida da campanha")
    void deveExecutarCicloDeVidaCampanha() throws Exception {
        // 1. Criar
        String criarPayload = """
            {
                "titulo": "Campanha Ciclo Vida",
                "textoOriginal": "Texto original",
                "canal": "APP",
                "taxa": 0.0400,
                "dataInicio": "2026-09-01",
                "dataFim": "2026-09-30"
            }
            """;

        String postResponse = mockMvc.perform(post("/api/v1/campanhas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(criarPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extrai o ID criado (ex: "id": 1)
        long campanhaId = Long.parseLong(postResponse.replaceAll(".*\"id\":\\s*(\\d+).*", "$1"));

        // 2. Buscar por ID
        mockMvc.perform(get("/api/v1/campanhas/" + campanhaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Campanha Ciclo Vida"))
                .andExpect(jsonPath("$.regra.canal").value("APP"));

        // 3. Listar ativas
        mockMvc.perform(get("/api/v1/campanhas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));

        // 4. Atualizar
        String atualizarPayload = """
            {
                "titulo": "Campanha Ciclo Vida Atualizada",
                "textoOriginal": "Texto alterado",
                "canal": "APP_PREMIUM",
                "taxa": 0.0600,
                "dataInicio": "2026-09-01",
                "dataFim": "2026-10-15"
            }
            """;

        mockMvc.perform(put("/api/v1/campanhas/" + campanhaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(atualizarPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Campanha Ciclo Vida Atualizada"))
                .andExpect(jsonPath("$.regra.canal").value("APP_PREMIUM"))
                .andExpect(jsonPath("$.regra.taxa").value(0.0600));

        // 5. Exclusão lógica (soft delete)
        mockMvc.perform(delete("/api/v1/campanhas/" + campanhaId))
                .andExpect(status().isNoContent());

        // 6. Tentar buscar após exclusão lógica deve retornar 404
        mockMvc.perform(get("/api/v1/campanhas/" + campanhaId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}