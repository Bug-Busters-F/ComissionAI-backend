package com.bugbusters.backend.dto.auditoria;

import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

public record FiltroAuditoriaDTO(
        String usuario,
        TipoAcaoAuditoria tipoAcao,
        TipoEntidadeAuditoria tipoEntidade,
        String entidadeId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataInicio,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataFim,
        Boolean sucesso
) {}

