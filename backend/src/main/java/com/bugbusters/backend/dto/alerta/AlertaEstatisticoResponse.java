package com.bugbusters.backend.dto.alerta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertaEstatisticoResponse(UUID id, UUID lotePreparacaoId, UUID vendaId, String matricula,
        LocalDate dataReferencia, String tipo, String metodo, String grupoComparavel, int tamanhoAmostra,
        BigDecimal valorObservado, BigDecimal mediaGrupo, BigDecimal desvioPadrao, BigDecimal score,
        String versaoBases, OffsetDateTime geradoEm) { }
