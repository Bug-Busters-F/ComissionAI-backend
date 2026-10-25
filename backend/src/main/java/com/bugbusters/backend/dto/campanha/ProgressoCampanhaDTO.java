package com.bugbusters.backend.dto.campanha;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
public record ProgressoCampanhaDTO(@NotNull @Min(1) @Max(4) Integer ultimaEtapa,
                                  @NotNull @Min(1) @Max(4) Integer etapaMaisAvancada,
                                  @NotNull Integer versaoProposta) {}
