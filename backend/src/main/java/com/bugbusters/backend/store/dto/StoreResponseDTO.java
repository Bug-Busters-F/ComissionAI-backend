package com.bugbusters.backend.store.dto;

import java.util.UUID;

public record StoreResponseDTO(
        UUID id,
        Integer code,
        String description) {
};
