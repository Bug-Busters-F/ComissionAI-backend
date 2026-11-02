package com.bugbusters.backend.service.auth;

/**
 * Ponto de extensão para identificação do usuário logado (integração com BACK-1/BACK-3).
 */
public interface UsuarioAtualProvider {

    /**
     * Retorna o identificador do usuário atual.
     *
     * @param responsavelPaliativo valor informado provisoriamente via payload/header (TODO BACK-1: remover quando houver SecurityContext)
     * @return identificador do usuário autenticado ou paliativo/SISTEMA
     */
    String obterUsuarioAtual(String responsavelPaliativo);
}
