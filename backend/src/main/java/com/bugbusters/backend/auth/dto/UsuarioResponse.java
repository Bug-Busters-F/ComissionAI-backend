package com.bugbusters.backend.auth.dto;

import com.bugbusters.backend.auth.*;
import java.util.UUID;
import java.util.List;

public record UsuarioResponse(UUID id, String nome, String email, PapelUsuario papel, String nomePerfil,
                              List<String> permissoes, boolean ativo) {
    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.getPapel().getNome(),
                PerfilPermissoes.permissoes(u.getPapel()), u.isAtivo());
    }
}
