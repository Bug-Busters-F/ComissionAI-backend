package com.bugbusters.backend.dto.alerta;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReprocessarAlertasRequest(@NotNull UUID lotePreparacaoId) { }
