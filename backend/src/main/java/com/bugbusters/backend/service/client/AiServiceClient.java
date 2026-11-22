package com.bugbusters.backend.service.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    /** IA-10: cabeçalho de rastreio devolvido pela IA e presente em todos os logs dela. */
    public static final String HEADER_CORRELACAO = "X-Correlation-Id";
    /** Chave do MDC reaproveitada quando um filtro já tiver definido o id da requisição. */
    public static final String MDC_CORRELACAO = "correlationId";

    public record ItemExecucaoPython(String id, boolean aplicada, BigDecimal comissao) {}

    public record ExecutarProgramaPythonResponse(
            boolean sucesso,
            String motor,
            int totalItens,
            List<ItemExecucaoPython> resultados,
            String erro,
            String detalhe,
            List<String> violacoes,
            String assinaturaPrograma
    ) {
        public ExecutarProgramaPythonResponse(boolean sucesso, String motor, int totalItens,
                List<ItemExecucaoPython> resultados, String erro, String detalhe, List<String> violacoes) {
            this(sucesso, motor, totalItens, resultados, erro, detalhe, violacoes, null);
        }
    }

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

    @Value("${ai.service.v2.gerar-programa-endpoint:/api/v2/gerar-programa}")
    private String v2GerarProgramaEndpoint;

    @Value("${ai.service.v2.simular-endpoint:/api/v2/simular}")
    private String v2SimularEndpoint;

    @Value("${ai.service.v2.anomalias-endpoint:/api/v2/anomalias/analisar}")
    private String v2AnomaliasEndpoint;

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
                    .header(HEADER_CORRELACAO, correlacaoId(endpoint))
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
                    .header(HEADER_CORRELACAO, correlacaoId(v2ExecutarProgramaEndpoint))
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
                    .header(HEADER_CORRELACAO, correlacaoId(uri))
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

    // ------------------------------------------------------------------ contratos da Sprint 3 (IA-10)
    // Métodos prontos para o BACK-11 (gerar/simular com cenários, calibração e explicações) e o BACK-12
    // (anomalias). O payload e a resposta seguem docs/CONTRATO-SPRINT3.md e docs/contratos/*.schema.json do
    // serviço de IA. Diferente de postarV2, um 4xx da IA (payload inválido, volume acima do limite) vira
    // BusinessException com a mensagem da IA, para chegar ao usuário em vez de "serviço indisponível".

    /** Gera e valida o programa antes da revisão técnica (POST /api/v2/gerar-programa, IA-2/IA-10). */
    public Map<String, Object> gerarProgramaV2(Map<String, Object> payload) {
        return postarContratoIa(v2GerarProgramaEndpoint, payload);
    }

    /**
     * Quatro cenários com orçamento, calibração ({@code calibrar=true}) e explicações por cenário
     * (POST /api/v2/simular, IA-5 a IA-7). Enviar {@code codigoAprovado} para não regerar código (ver §5 do contrato).
     */
    public Map<String, Object> simularV2(Map<String, Object> payload) {
        return postarContratoIa(v2SimularEndpoint, payload);
    }

    /** Detecção estatística diária de anomalias por grupo comparável (POST /api/v2/anomalias/analisar, IA-8). */
    public Map<String, Object> analisarAnomaliasV2(Map<String, Object> payload) {
        return postarContratoIa(v2AnomaliasEndpoint, payload);
    }

    private Map<String, Object> postarContratoIa(String uri, Map<String, Object> payload) {
        try {
            validarChaveInterna();
            return aiRestClient.post()
                    .uri(uri)
                    .header(authHeader, authKey)
                    .header(HEADER_CORRELACAO, correlacaoId(uri))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String mensagem = extrairMensagemErroSandbox(res.getBody().readAllBytes(), res.getStatusCode().value());
                        throw new BusinessException("O serviço de IA recusou a requisição: " + mensagem);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new AiServiceUnavailableException("O serviço de IA retornou status HTTP " + res.getStatusCode().value());
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (BusinessException | AiServiceUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                throw new AiTimeoutException("O serviço de IA excedeu o tempo limite configurado.");
            }
            throw new AiServiceUnavailableException("Não foi possível conectar ao serviço de IA em Python. Verifique se o módulo está ativo.");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                String mensagem = extrairMensagemErroSandbox(ex.getResponseBodyAsByteArray(), ex.getStatusCode().value());
                throw new BusinessException("O serviço de IA recusou a requisição: " + mensagem);
            }
            throw new AiServiceUnavailableException("Erro retornado pelo serviço de IA: " + ex.getStatusText());
        } catch (RestClientException ex) {
            throw new AiInvalidResponseException("O serviço de IA retornou uma resposta em formato inválido.", ex);
        }
    }

    /**
     * IA-10: id de rastreio enviado em toda chamada à IA. Reaproveita o MDC quando houver; senão gera um por
     * chamada e registra no log, para cruzar com os logs da IA (que repetem o id em cada linha).
     */
    private String correlacaoId(String uri) {
        String id = MDC.get(MDC_CORRELACAO);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        log.info("Chamada ao serviço de IA {} com {}={}", uri, HEADER_CORRELACAO, id);
        return id;
    }

    private void validarChaveInterna() {
        if (authKey == null || authKey.isBlank()) {
            throw new AiServiceUnavailableException("Chave interna do serviço de IA não configurada.");
        }
    }
}
