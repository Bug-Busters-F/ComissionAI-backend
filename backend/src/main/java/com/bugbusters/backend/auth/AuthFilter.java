package com.bugbusters.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
    private final AuthService service;
    @Value("${ai.service.auth.header:X-Internal-Api-Key}")
    private String internalHeader;
    @Value("${ai.service.auth.key:}")
    private String internalKey;

    public AuthFilter(AuthService service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // A credencial de serviço só autoriza a superfície de consultas da IA.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String supplied = request.getHeader(internalHeader);
        if (path.startsWith("/api/v1/consultas-ia/") && internalKey != null && !internalKey.isBlank()
                && supplied != null && MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "SERVICO_IA", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICO_IA"))));
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                Usuario usuario = service.usuarioDaSessao(authorization.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(
                        usuario, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPapel().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthenticationException ignored) {
                // A cadeia de segurança converterá token inválido em resposta 401.
            }
        }
        filterChain.doFilter(request, response);
    }
}
