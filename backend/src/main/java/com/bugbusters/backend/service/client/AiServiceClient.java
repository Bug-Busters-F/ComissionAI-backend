package com.bugbusters.backend.service.client;

import java.net.SocketTimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraRequest;
import com.bugbusters.backend.dto.interpretador.InterpretacaoRegraResponse;
import com.bugbusters.backend.exception.AiServiceUnavailableException;
import com.bugbusters.backend.exception.AiTimeoutException;

@Component 
public class AiServiceClient {
    private RestClient aiRestClient;
    private final String endpoint;

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
            return aiRestClient.post()
                    .uri(endpoint)
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
}
