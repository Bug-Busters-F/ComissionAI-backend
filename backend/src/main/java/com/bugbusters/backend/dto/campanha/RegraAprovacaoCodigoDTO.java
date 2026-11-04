package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.model.StatusAprovacaoCodigo;
import java.time.OffsetDateTime;

public record RegraAprovacaoCodigoDTO(
        Long regraId,
        String nome,
        StatusAprovacaoCodigo statusAprovacaoCodigo,
        String hashArtefatosCodigo,
        String pythonEquivalente,
        OffsetDateTime codigoAprovadoEm,
        String codigoAprovadoPor,
        OffsetDateTime codigoInvalidadoEm,
        String motivoInvalidacaoCodigo
) {}
