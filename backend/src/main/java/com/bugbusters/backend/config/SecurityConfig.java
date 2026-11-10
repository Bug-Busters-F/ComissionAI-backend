package com.bugbusters.backend.config;

import com.bugbusters.backend.auth.AuthFilter;
import org.springframework.context.annotation.*;
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
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity h, AuthFilter f) throws Exception {
        return h.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/login", "/swagger-ui/**", "/v3/api-docs/**", "/h2-console/**")
                        .permitAll().requestMatchers("/api/v1/usuarios/**").hasRole("ADMIN").anyRequest()
                        .authenticated())
                .addFilterBefore(f, UsernamePasswordAuthenticationFilter.class).build();
    }
}
