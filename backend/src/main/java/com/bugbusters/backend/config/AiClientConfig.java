package com.bugbusters.backend.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration 
public class AiClientConfig {

    @Bean
    public RestClient.Builder aiRestClientBuilder(
            @Value("${ai.service.url}") String baseUrl,
            @Value("${ai.service.connect-timeout-ms}") int connectTimeout,
            @Value("${ai.service.read-timeout-ms}") int readTimeout) {
                
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        factory.setReadTimeout(Duration.ofMillis(readTimeout));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);
    }

    @Bean
    public RestClient aiRestClient(RestClient.Builder aiRestClientBuilder) {
        return aiRestClientBuilder.build();
    }
}
