package com.bugbusters.backend.auth.dto;

import com.bugbusters.backend.auth.*;
import java.util.UUID;

public record UsuarioResponse(UUID id, String nome, String email, PapelUsuario papel, boolean ativo) {
    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.isAtivo());
    }
}
