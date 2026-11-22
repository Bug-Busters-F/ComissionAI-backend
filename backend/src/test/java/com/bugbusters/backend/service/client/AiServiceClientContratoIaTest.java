package com.bugbusters.backend.service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bugbusters.backend.exception.AiServiceUnavailableException;
import com.bugbusters.backend.exception.BusinessException;

/**
 * IA-10 (BUG-123): contrato do backend com as rotas da Sprint 3 do serviço de IA. Teste unitário, sem contexto
 * Spring: confere rotas, chave interna, X-Correlation-Id e o tratamento de 4xx/5xx dos métodos novos.
 */
class AiServiceClientContratoIaTest {

    private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private MockRestServiceServer server;
    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ia");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiServiceClient(builder.build(), "/api/v1/interpretar");
        ReflectionTestUtils.setField(client, "authHeader", "X-Internal-Api-Key");
        ReflectionTestUtils.setField(client, "authKey", "segredo");
        ReflectionTestUtils.setField(client, "v2ExecutarProgramaEndpoint", "/api/v2/executar-programa");
        ReflectionTestUtils.setField(client, "v2GerarProgramaEndpoint", "/api/v2/gerar-programa");
        ReflectionTestUtils.setField(client, "v2SimularEndpoint", "/api/v2/simular");
        ReflectionTestUtils.setField(client, "v2AnomaliasEndpoint", "/api/v2/anomalias/analisar");
    }

    @Test
    void simularV2EnviaChaveECorrelacaoEDevolveOCorpo() {
        server.expect(requestTo("http://ia/api/v2/simular"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "segredo"))
                .andExpect(header(AiServiceClient.HEADER_CORRELACAO, Matchers.matchesPattern(UUID_REGEX)))
                .andExpect(jsonPath("$.calibrar").value(true))
                .andRespond(withSuccess("{\"statusSugestoes\":\"OK\",\"calibracoes\":[]}", MediaType.APPLICATION_JSON));

        Map<String, Object> resposta = client.simularV2(Map.of("competencia", "2025-11", "calibrar", true));

        assertThat(resposta).containsEntry("statusSugestoes", "OK");
        server.verify();
    }

    @Test
    void gerarProgramaEAnomaliasUsamAsRotasDoContrato() {
        server.expect(requestTo("http://ia/api/v2/gerar-programa"))
                .andRespond(withSuccess("{\"assinatura\":\"abc\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ia/api/v2/anomalias/analisar"))
                .andExpect(header(AiServiceClient.HEADER_CORRELACAO, Matchers.matchesPattern(UUID_REGEX)))
                .andRespond(withSuccess("{\"alertas\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.gerarProgramaV2(Map.of("competencia", "2025-11"))).containsEntry("assinatura", "abc");
        assertThat(client.analisarAnomaliasV2(Map.of("competencia", "2025-11", "vendas", List.of())))
                .containsEntry("alertas", List.of());
        server.verify();
    }

    @Test
    void erro4xxDaIaViraBusinessExceptionComAMensagemDaIa() {
        server.expect(requestTo("http://ia/api/v2/anomalias/analisar"))
                .andRespond(withStatus(HttpStatus.PAYLOAD_TOO_LARGE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"O recorte tem 300000 vendas; o limite da análise é 200000.\","
                                + "\"error_code\":\"VOLUME_EXCEDIDO\"}"));

        assertThatThrownBy(() -> client.analisarAnomaliasV2(Map.of("competencia", "2025-11")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limite da análise é 200000");
    }

    @Test
    void erro5xxDaIaViraIndisponibilidade() {
        server.expect(requestTo("http://ia/api/v2/simular"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.simularV2(Map.of()))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("502");
    }

    @Test
    void executarProgramaPythonTambemEnviaCorrelacao() {
        server.expect(requestTo("http://ia/api/v2/executar-programa"))
                .andExpect(header(AiServiceClient.HEADER_CORRELACAO, Matchers.matchesPattern(UUID_REGEX)))
                .andRespond(withSuccess("{\"sucesso\":true,\"motor\":\"PYTHON_SANDBOX\",\"totalItens\":1,"
                        + "\"resultados\":[{\"id\":\"v1#100\",\"aplicada\":true,\"comissao\":\"42.00\"}],"
                        + "\"assinaturaPrograma\":\"abc\",\"contexto\":{\"modo\":\"SIMULACAO\"}}",
                        MediaType.APPLICATION_JSON));

        AiServiceClient.ExecutarProgramaPythonResponse resposta = client.executarProgramaPython(Map.of("itens", List.of()));

        assertThat(resposta.resultados()).singleElement()
                .satisfies(item -> assertThat(item.comissao()).isEqualByComparingTo("42.00"));
        server.verify();
    }

    @Test
    void semChaveInternaNaoChamaAIa() {
        ReflectionTestUtils.setField(client, "authKey", "");

        assertThatThrownBy(() -> client.simularV2(Map.of()))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("Chave interna");
        server.verify();
    }
}
