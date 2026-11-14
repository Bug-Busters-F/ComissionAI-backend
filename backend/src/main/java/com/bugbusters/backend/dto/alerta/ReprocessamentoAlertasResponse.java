package com.bugbusters.backend.dto.alerta;

import java.util.UUID;

public record ReprocessamentoAlertasResponse(UUID lotePreparacaoId, String versaoBases,
        int alertasGerados, int alertasSubstituidos, boolean semAmostra) { }
