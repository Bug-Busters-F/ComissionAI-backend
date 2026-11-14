package com.bugbusters.backend.support;

import com.bugbusters.backend.service.client.AiServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Helper de teste para simular o endpoint `/api/v2/executar-programa` do serviço Python em testes `@SpringBootTest`,
 * capturando o código-fonte enviado pelo backend e devolvendo resultados determinísticos sem depender de rede externa.
 */
public final class PythonSandboxMockSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PADRAO_TAXA_DECIMAL = Pattern.compile("taxa\\s*=\\s*Decimal\\('([0-9]+(?:\\.[0-9]+)?)'\\)");
    private static final Pattern PADRAO_TAXA_SIMPLES = Pattern.compile("taxa\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern PADRAO_VIGENCIA = Pattern.compile("\\('(\\d{4}-\\d{2}-\\d{2})'\\s*<=\\s*data_venda\\s*<=\\s*'(\\d{4}-\\d{2}-\\d{2})'\\)");

    private final AiServiceClient aiServiceClient;
    private final RestClient originalClient;
    private final MockRestServiceServer server;
    private final List<String> codigosExecutados = new ArrayList<>();
    private final List<JsonNode> requisicoesRecebidas = new ArrayList<>();

    private PythonSandboxMockSupport(AiServiceClient aiServiceClient, RestClient.Builder builder) {
        this.aiServiceClient = aiServiceClient;
        this.originalClient = aiServiceClient.getAiRestClient();
        this.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        aiServiceClient.setAiRestClient(builder.build());
    }

    public static PythonSandboxMockSupport instalar(RestClient.Builder builder, AiServiceClient client) {
        return new PythonSandboxMockSupport(client, builder);
    }

    public PythonSandboxMockSupport registrarExecucaoPadrao() {
        return registrarCalculador((codigoFonte, venda) -> avaliarPadrao(codigoFonte, venda));
    }

    public PythonSandboxMockSupport registrarCalculador(BiFunction<String, JsonNode, BigDecimal> avaliador) {
        server.expect(ExpectedCount.manyTimes(), requestTo(Matchers.endsWith("/api/v2/executar-programa")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    byte[] bodyBytes = ((MockClientHttpRequest) request).getBodyAsBytes();
                    JsonNode reqJson = MAPPER.readTree(bodyBytes);
                    requisicoesRecebidas.add(reqJson);
                    String codigo = reqJson.path("codigoFonte").asText("");
                    codigosExecutados.add(codigo);

                    List<Map<String, Object>> resultados = new ArrayList<>();
                    for (JsonNode item : reqJson.path("itens")) {
                        String id = item.path("id").asText();
                        JsonNode venda = item.path("venda");
                        BigDecimal comissao = avaliador.apply(codigo, venda);
                        Map<String, Object> respItem = new LinkedHashMap<>();
                        respItem.put("id", id);
                        if (comissao == null) {
                            respItem.put("aplicada", false);
                            respItem.put("comissao", null);
                        } else {
                            respItem.put("aplicada", true);
                            respItem.put("comissao", comissao.setScale(2, RoundingMode.HALF_UP).toPlainString());
                        }
                        resultados.add(respItem);
                    }

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("sucesso", true);
                    body.put("motor", "PYTHON_SANDBOX");
                    body.put("totalItens", resultados.size());
                    body.put("resultados", resultados);
                    return withSuccess(MAPPER.writeValueAsString(body), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });
        return this;
    }

    public PythonSandboxMockSupport registrarErroSandbox(HttpStatus status, String detalhe, List<String> violacoes) {
        server.expect(ExpectedCount.manyTimes(), requestTo(Matchers.endsWith("/api/v2/executar-programa")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("sucesso", false);
                    body.put("motor", "PYTHON_SANDBOX");
                    body.put("detalhe", detalhe);
                    body.put("violacoes", violacoes != null ? violacoes : List.of());
                    return withStatus(status)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(MAPPER.writeValueAsString(body))
                            .createResponse(request);
                });
        return this;
    }

    public List<String> getCodigosExecutados() {
        return codigosExecutados;
    }

    public List<JsonNode> getRequisicoesRecebidas() {
        return requisicoesRecebidas;
    }

    public void restaurar() {
        aiServiceClient.setAiRestClient(originalClient);
    }

    private static BigDecimal avaliarPadrao(String codigo, JsonNode venda) {
        if (!venda.path("ignorar_vigencia").asBoolean(false)) {
            Matcher vigMatcher = PADRAO_VIGENCIA.matcher(codigo);
            if (vigMatcher.find()) {
                String data = venda.path("data_venda").asText("");
                if (data.length() >= 10) {
                    String dt = data.substring(0, 10);
                    if (dt.compareTo(vigMatcher.group(1)) < 0 || dt.compareTo(vigMatcher.group(2)) > 0) {
                        return null;
                    }
                }
            }
        }

        double valor = venda.hasNonNull("valor_venda") ? venda.get("valor_venda").asDouble() : venda.path("valor").asDouble(0.0);
        if (valor <= 0.0) {
            return null;
        }

        BigDecimal taxa = null;
        Matcher decMatcher = PADRAO_TAXA_DECIMAL.matcher(codigo);
        if (decMatcher.find()) {
            taxa = new BigDecimal(decMatcher.group(1));
        } else {
            Matcher simpleMatcher = PADRAO_TAXA_SIMPLES.matcher(codigo);
            if (simpleMatcher.find()) {
                taxa = new BigDecimal(simpleMatcher.group(1));
            }
        }

        if (taxa == null) {
            return null;
        }
        return BigDecimal.valueOf(valor).multiply(taxa).setScale(2, RoundingMode.HALF_UP);
    }
}

