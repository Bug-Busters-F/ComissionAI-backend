package com.bugbusters.backend.dto.auditoria;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Métricas e resumo executivo da trilha de auditoria geral")
public record ResumoAuditoriaDTO(
        long totalRegistros,
        long totalSucessos,
        long totalFalhas,
        long totalUltimas24Horas,
        Map<String, Long> registrosPorTipoAcao,
        Map<String, Long> registrosPorEntidade
) {}

