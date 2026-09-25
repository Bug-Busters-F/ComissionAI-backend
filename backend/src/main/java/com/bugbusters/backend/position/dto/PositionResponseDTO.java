package com.bugbusters.backend.position.dto;

import java.util.UUID;

public record PositionResponseDTO(
    UUID id,
    Integer code,
    String description
) {
};
