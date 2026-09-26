package com.bugbusters.backend.registration.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.store.Store;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Representação da matrícula individual registrada no sistema")
public record RegistrationResponseDTO(

    @Schema(description = "Identificador interno gerado pelo sistema", example = "1")
    UUID id,

    @Schema(description = "Posição relacionada")
    Position position,

    @Schema(description = "Loja onde a venda foi realizada", example = "LOJA_SP_01")
    Store store,

    @Schema(description = "Data de admissão", example = "2026-09-13")
    LocalDate admissDate,

    @Schema(description = "Data de demissão", example = "2026-09-13")
    LocalDate demissDate

) {}