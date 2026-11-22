package com.bugbusters.backend.auth.dto;

import com.bugbusters.backend.auth.PapelUsuario;
import jakarta.validation.constraints.*;

public record UsuarioRequest(@NotBlank String nome, @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String senha, @NotNull PapelUsuario papel) {
}
