package com.bugbusters.backend.service.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(value = UsuarioAtualProvider.class, ignored = SistemaUsuarioAtualProvider.class)
public class SistemaUsuarioAtualProvider implements UsuarioAtualProvider {

    @Override
    public String obterUsuarioAtual(String responsavelPaliativo) {
        // TODO BACK-1: obter identidade confiável do SecurityContext e ignorar/remover parâmetro paliativo
        if (responsavelPaliativo != null && !responsavelPaliativo.isBlank()) {
            return responsavelPaliativo.trim();
        }
        return "SISTEMA";
    }
}
