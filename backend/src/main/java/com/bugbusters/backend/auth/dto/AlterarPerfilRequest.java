package com.bugbusters.backend.auth.dto;
import com.bugbusters.backend.auth.PapelUsuario;
import jakarta.validation.constraints.NotNull;
public record AlterarPerfilRequest(@NotNull PapelUsuario papel) {}
