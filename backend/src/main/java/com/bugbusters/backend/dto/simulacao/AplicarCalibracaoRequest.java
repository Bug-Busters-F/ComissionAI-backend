package com.bugbusters.backend.dto.simulacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record AplicarCalibracaoRequest(@NotBlank String cenario, @NotNull Long revisaoEsperada,
        @NotBlank String assinaturaProgramaEsperada) {}
