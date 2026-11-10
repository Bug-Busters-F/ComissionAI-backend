package com.bugbusters.backend.config;

import com.bugbusters.backend.auth.AuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String ADMIN = "ADMINISTRADOR";
    private static final String NEGOCIOS = "ANALISTA_NEGOCIOS";
    private static final String GERENTE = "GERENTE_VENDAS";
    private static final String CODIGO = "ANALISTA_CODIGO";
    private static final String AUDITOR = "AUDITOR";
    private static final String[] TODOS = {ADMIN, NEGOCIOS, GERENTE, CODIGO, AUDITOR};

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain security(HttpSecurity http, AuthFilter filter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                com.bugbusters.backend.auth.SecurityErrors.write(response, request, 401,
                                        "Sessão inválida ou expirada. Entre novamente."))
                        .accessDeniedHandler((request, response, exception) ->
                                com.bugbusters.backend.auth.SecurityErrors.write(response, request, 403,
                                        "Seu perfil não tem permissão para esta operação.")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/swagger-ui/**", "/v3/api-docs/**", "/h2-console/**").permitAll()
                        .requestMatchers("/api/v1/usuarios/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/**").hasAnyRole(TODOS)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/me/**").hasAnyRole(TODOS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").hasAnyRole(TODOS)
                        .requestMatchers(HttpMethod.GET, "/api/v1/consultas-ia/**").hasAnyRole(ADMIN, NEGOCIOS, CODIGO, GERENTE, AUDITOR, "SERVICO_IA")
                        .requestMatchers(HttpMethod.POST, "/api/v1/consultas-ia/**").hasAnyRole(ADMIN, NEGOCIOS, CODIGO, GERENTE, AUDITOR, "SERVICO_IA")
                        .requestMatchers("/api/v1/consultas-ia/**").denyAll()
                        // Logs: somente administrador, gerente e auditor.
                        .requestMatchers(HttpMethod.GET, "/api/v1/logs-calculo/**").hasAnyRole(ADMIN, GERENTE, AUDITOR)
                        // Auditor consulta as bases; nenhuma escrita é liberada por esta regra.
                        .requestMatchers(HttpMethod.GET, "/api/v1/imports/**", "/api/v1/vendas/**", "/api/v1/matriculas/**",
                                "/api/v1/bases-comissao/**", "/api/v1/comissoes/**").hasAnyRole(ADMIN, GERENTE, AUDITOR)
                        // Importação/correção de bases e cálculos definitivos: administrador ou gerente.
                        .requestMatchers("/api/v1/imports/**", "/api/v1/vendas/**", "/api/v1/matriculas/**",
                                "/api/v1/bases-comissao/**", "/api/v1/comissoes/**").hasAnyRole(ADMIN, GERENTE)
                        // Consulta de negócio é permitida a todos os perfis autenticados.
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole(TODOS)
                        // Etapa 4 e cálculo de competência: gerente publica e solicita cálculo.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/campanhas/*/estado")
                            .hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/campanhas/*/progresso").hasAnyRole(ADMIN, GERENTE, NEGOCIOS, CODIGO)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/campanhas/*/orcamento").hasAnyRole(ADMIN, GERENTE, NEGOCIOS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/campanhas/*/encaminhar-gerente").hasAnyRole(ADMIN, GERENTE, NEGOCIOS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/campanhas/*/aprovacao-codigo").hasAnyRole(ADMIN, CODIGO, NEGOCIOS)
                        .requestMatchers("/api/v1/campanhas/*/preparacao-bases/**", "/api/v1/campanhas/*/anexos-base/**", "/api/v1/campanhas/*/cobertura-fechamento/**").hasAnyRole(ADMIN, GERENTE, NEGOCIOS)
                        .requestMatchers(HttpMethod.POST, "/api/v1/campanhas/*/ativar", "/api/v1/comissoes/calcular/**",
                                "/api/v1/execucoes/**", "/api/v1/comissoes/fechamento/**").hasAnyRole(ADMIN, GERENTE)
                        // O aceite da simulação pertence à etapa 3; publicação continua gerencial.
                        .requestMatchers(HttpMethod.POST, "/api/v1/simulacoes/*/aceite").hasAnyRole(ADMIN, NEGOCIOS, GERENTE)
                        // Etapa 1 e 3: analista de negócios cria/ajusta campanha e simula.
                        .requestMatchers(HttpMethod.POST, "/api/v1/campanhas/**", "/api/v1/simulacoes/**",
                                "/api/v1/referencias/**", "/api/v1/regras/**").hasAnyRole(ADMIN, NEGOCIOS)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/campanhas/**").hasAnyRole(ADMIN, NEGOCIOS, CODIGO)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/campanhas/**", "/api/v1/regras/**").hasAnyRole(ADMIN, NEGOCIOS)
                        // Etapa 2: revisão do programa e artefatos de código.
                        .requestMatchers("/api/v1/interpretador/**").hasAnyRole(ADMIN, CODIGO, NEGOCIOS)
                        .requestMatchers("/api/v1/**").hasRole(ADMIN)
                        .anyRequest().denyAll())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
