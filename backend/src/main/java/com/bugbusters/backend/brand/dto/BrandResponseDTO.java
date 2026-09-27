package com.bugbusters.backend.brand.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respostas relacionada a Marcas")
public record BrandResponseDTO(

        UUID id,

        Integer code,

        String description

) {};
