package com.bugbusters.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

@Configuration 
public class OpenApiConfig {
    @Bean 
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("Bearer")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .info(new Info()
                .title("Bug Busters API - Motor de Regras e Comissões")
                .version("1.0.0")
                .description("""
                    Contratos de integração para gerenciamento dinâmico de 
                    regras, simulação, ingestão de bases e interpretação via
                    IA (Dom Rock - 6º ADS).
                    """)
                .contact(new Contact()
                    .name("Equipe Bug Busters")
                    .email("bugbustersf@gmail.com")));
    }
}
