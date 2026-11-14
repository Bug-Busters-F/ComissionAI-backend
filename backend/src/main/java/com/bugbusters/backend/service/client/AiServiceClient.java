package com.bugbusters.backend.service.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraRequest;
import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraResponse;
import com.bugbusters.backend.exception.AiInvalidResponseException;
import com.bugbusters.backend.exception.AiServiceUnavailableException;
import com.bugbusters.backend.exception.AiTimeoutException;
import com.bugbusters.backend.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component 
public class AiServiceClient {
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    public record ItemExecucaoPython(String id, boolean aplicada, BigDecimal comissao) {}

    public record ExecutarProgramaPythonResponse(
            boolean sucesso,
            String motor,
            int totalItens,
            List<ItemExecucaoPython> resultados,
            String erro,
            String detalhe,
            List<String> violacoes
    ) {}

    private RestClient aiRestClient;
    private final String endpoint;

    @Value("${ai.service.auth.header:X-Internal-Api-Key}")
    private String authHeader;

    @Value("${ai.service.auth.key:}")
    private String authKey;

    @Value("${ai.service.v2.endpoint:/api/v2/interpretar}")
    private String v2Endpoint;

    @Value("${ai.service.v2.reinterpretar-endpoint:/api/v2/reinterpretar}")
    private String v2ReinterpretarEndpoint;

    @Value("${ai.service.v2.atualizar-artefatos-endpoint:/api/v2/atualizar-artefatos}")
    private String v2AtualizarArtefatosEndpoint;

    @Value("${ai.service.v2.executar-programa-endpoint:/api/v2/executar-programa}")
    private String v2ExecutarProgramaEndpoint;

    public AiServiceClient(RestClient aiRestClient, @Value("${ai.service.endpoint:/api/v1/interpretar}") String endpoint) {
        this.aiRestClient = aiRestClient;
        this.endpoint = endpoint;
    }

    public void setAiRestClient(RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    public RestClient getAiRestClient() {
        return this.aiRestClient;
    }

    public InterpretacaoRegraResponse chamarServicoPython(InterpretacaoRegraRequest request) {
        try {
            validarChaveInterna();
            return aiRestClient.post()
                    .uri(endpoint)
                    .header(authHeader, authKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new AiServiceUnavailableException("O serviço de IA retornou status HTTP " + res.getStatusCode().value());
                    })
                    .body(InterpretacaoRegraResponse.class);
        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                throw new AiTimeoutException("O processamento da linguagem natural excedeu o tempo limite configurado.");
            }
            throw new AiServiceUnavailableException("Não foi possível conectar ao serviço de IA em Python. Verifique se o módulo está ativo.");
        } catch (RestClientResponseException ex) {
            throw new AiServiceUnavailableException("Erro retornado pelo orquestrador de IA: " + ex.getStatusText());
        }
    }

    /**
     * Chama o agente de interpretação do serviço Python (/api/v2/interpretar: lista de propostas com consultas
     * ao cadastro). A resposta vem como mapa e é convertida para PropostaRegraDTO pelo InterpretadorService.
     */
    public Map<String, Object> chamarServicoPythonV2(Map<String, Object> payload) {
        return postarV2(v2Endpoint, payload);
    }

    /**
     * Reinterpretação localizada de uma regra editada (/api/v2/reinterpretar): devolve as propostas que
     * substituem o bloco alvo (uma ou, se o texto foi dividido, várias).
     */
    public Map<String, Object> reinterpretarBlocoV2(Map<String, Object> payload) {
        return postarV2(v2ReinterpretarEndpoint, payload);
    }

    /**
     * Gera explicação e código Python do bloco a partir dos campos atuais (/api/v2/atualizar-artefatos).
     * Não usa o LLM: é determinístico e não reinterpreta o texto.
     */
    public Map<String, Object> atualizarArtefatosV2(Map<String, Object> payload) {
        return postarV2(v2AtualizarArtefatosEndpoint, payload);
    }

    /**
     * Executa o programa Python aprovado (`calcular_comissao`) em lote dentro do sandbox isolado (/api/v2/executar-programa).
     * Opera em fail-closed: erros de sintaxe, AST, runtime ou indisponibilidade interrompem a execução sem fallback para Java.
     */
    public ExecutarProgramaPythonResponse executarProgramaPython(Map<String, Object> payload) {
        try {
            validarChaveInterna();
            ExecutarProgramaPythonResponse response = aiRestClient.post()
                    .uri(v2ExecutarProgramaEndpoint)
                    .header(authHeader, authKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String mensagem = extrairMensagemErroSandbox(res.getBody().readAllBytes(), res.getStatusCode().value());
                        throw new BusinessException("Falha na execução do programa Python no sandbox: " + mensagem);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new AiServiceUnavailableException("O serviço de sandbox Python retornou erro HTTP " + res.getStatusCode().value());
                    })
                    .body(ExecutarProgramaPythonResponse.class);

            if (response == null || !response.sucesso() || response.resultados() == null) {
                String detalhe = response != null && response.detalhe() != null
                        ? response.detalhe()
                        : "Resposta inválida ou vazia do sandbox Python.";
                throw new BusinessException("Execução no sandbox Python falhou: " + detalhe);
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                throw new AiTimeoutException("A execução do programa Python no sandbox excedeu o tempo limite configurado.");
            }
            throw new AiServiceUnavailableException("Não foi possível conectar ao serviço de IA para executar o programa Python. Verifique se o módulo Python está ativo.");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                String mensagem = extrairMensagemErroSandbox(ex.getResponseBodyAsByteArray(), ex.getStatusCode().value());
                throw new BusinessException("Falha na execução do programa Python no sandbox: " + mensagem);
            }
            throw new AiServiceUnavailableException("Erro retornado pelo sandbox Python: " + ex.getStatusText());
        } catch (RestClientException ex) {
            throw new AiInvalidResponseException("O sandbox Python retornou uma resposta em formato inválido.", ex);
        }
    }

    private String extrairMensagemErroSandbox(byte[] bodyBytes, int statusCode) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return "HTTP " + statusCode;
        }
        String raw = new String(bodyBytes, StandardCharsets.UTF_8);
        try {
            JsonNode json = ERROR_MAPPER.readTree(raw);
            List<String> partes = new ArrayList<>();
            if (json.hasNonNull("detalhe") && !json.get("detalhe").asText().isBlank()) {
                partes.add(json.get("detalhe").asText());
            } else if (json.hasNonNull("detail") && !json.get("detail").asText().isBlank()) {
                partes.add(json.get("detail").asText());
            }
            if (json.has("violacoes") && json.get("violacoes").isArray() && !json.get("violacoes").isEmpty()) {
                List<String> vs = new ArrayList<>();
                json.get("violacoes").forEach(v -> vs.add(v.asText()));
                String vStr = String.join("; ", vs);
                if (partes.isEmpty() || !partes.get(0).equals(vStr)) {
                    partes.add("Violações: " + vStr);
                }
            }
            return partes.isEmpty() ? raw : String.join(" | ", partes);
        } catch (IOException ignored) {
            return raw;
        }
    }

    private Map<String, Object> postarV2(String uri, Map<String, Object> payload) {
        try {
            validarChaveInterna();
            return aiRestClient.post()
                    .uri(uri)
                    .header(authHeader, authKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new AiServiceUnavailableException("O serviço de IA retornou status HTTP " + res.getStatusCode().value());
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                throw new AiTimeoutException("O processamento da linguagem natural excedeu o tempo limite configurado.");
            }
            throw new AiServiceUnavailableException("Não foi possível conectar ao serviço de IA em Python. Verifique se o módulo está ativo.");
        } catch (RestClientResponseException ex) {
            throw new AiServiceUnavailableException("Erro retornado pelo orquestrador de IA: " + ex.getStatusText());
        } catch (RestClientException ex) {
            throw new AiInvalidResponseException("O serviço de IA retornou uma resposta em formato inválido.", ex);
        }
    }

    private void validarChaveInterna() {
        if (authKey == null || authKey.isBlank()) {
            throw new AiServiceUnavailableException("Chave interna do serviço de IA não configurada.");
        }
    }
}
