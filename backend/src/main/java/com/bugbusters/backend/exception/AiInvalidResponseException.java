package com.bugbusters.backend.exception;

public class AiInvalidResponseException extends RuntimeException {
    public AiInvalidResponseException(String message) {
        super(message);
    }

    public AiInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
