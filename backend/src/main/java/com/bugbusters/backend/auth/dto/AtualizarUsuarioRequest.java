package com.bugbusters.backend.auth.dto;
import jakarta.validation.constraints.*;
public record AtualizarUsuarioRequest(@NotBlank String nome, @NotBlank @Email String email) {}
