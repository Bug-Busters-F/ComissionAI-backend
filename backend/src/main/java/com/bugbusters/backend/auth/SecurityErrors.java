package com.bugbusters.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class SecurityErrors {
    private static final ObjectMapper JSON = new ObjectMapper();
    private SecurityErrors() { }
    public static void write(HttpServletResponse response, HttpServletRequest request,
            int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSON.writeValue(response.getWriter(), Map.of("timestamp", OffsetDateTime.now().toString(),
                "status", status, "error", status == 401 ? "Unauthorized" : "Forbidden",
                "message", message, "path", request.getRequestURI(), "validacoes", List.of()));
    }
}
