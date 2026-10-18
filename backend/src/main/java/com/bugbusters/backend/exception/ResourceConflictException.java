package com.bugbusters.backend.exception;

import com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO;
import java.util.List;

public class ResourceConflictException extends RuntimeException {
    private final List<CampoInvalidoDTO> validacoes;

    public ResourceConflictException(String message) {
        this(message, List.of());
    }

    public ResourceConflictException(String message, List<CampoInvalidoDTO> validacoes) {
        super(message);
        this.validacoes = validacoes != null ? validacoes : List.of();
    }

    public List<CampoInvalidoDTO> getValidacoes() {
        return validacoes;
    }
}