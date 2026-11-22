package com.bugbusters.backend.service.auth;

import com.bugbusters.backend.auth.Usuario;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = UsuarioAtualProvider.class, ignored = SistemaUsuarioAtualProvider.class)
public class SistemaUsuarioAtualProvider implements UsuarioAtualProvider {

    @Override
    public String obterUsuarioAtual(String responsavelPaliativo) {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal() : null;
        if (principal instanceof Usuario usuario && usuario.getEmail() != null && !usuario.getEmail().isBlank()) {
            return usuario.getEmail().trim();
        }
        if (principal instanceof String s && !s.isBlank() && !"anonymousUser".equalsIgnoreCase(s)) {
            return s.trim();
        }
        if (responsavelPaliativo != null && !responsavelPaliativo.isBlank()) {
            return responsavelPaliativo.trim();
        }
        return "SISTEMA";
    }
}
