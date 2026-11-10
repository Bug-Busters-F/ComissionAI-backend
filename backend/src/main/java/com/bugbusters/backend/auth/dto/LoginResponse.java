package com.bugbusters.backend.auth.dto;

import java.time.OffsetDateTime;

public record LoginResponse(String token, String tipo, OffsetDateTime expiraEm, UsuarioResponse usuario) {
}
