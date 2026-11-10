package com.bugbusters.backend.auth.dto;
import jakarta.validation.constraints.*;
public record AlterarSenhaRequest(@NotBlank String senhaAtual, @NotBlank @Size(min=8,max=72) String novaSenha) {}
