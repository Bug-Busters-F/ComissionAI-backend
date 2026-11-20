package com.bugbusters.backend.dto.auditoria;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resumo agregado de atividade e perfil de um usuário visível ao solicitante.
 */
@Schema(description = "Resumo agregado de logs por usuário autorizado (BACK-17 / FRONT-12)")
public record AuditoriaUsuarioResumoDTO(
        String usuario,
        UUID usuarioId,
        String nome,
        String email,
        String papel,
        String nomePerfil,
        Boolean ativo,
        long totalEventosAuditoria,
        long totalLogsCalculo,
        long totalSucessos,
        long totalFalhas,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime ultimaAtividadeEm
) {}
