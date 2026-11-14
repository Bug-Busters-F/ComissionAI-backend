package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.model.StatusAprovacaoCodigo;
import java.time.OffsetDateTime;
import java.util.List;

public record AprovacaoCodigoCampanhaResponse(
        Long campanhaId,
        Integer versaoNumero,
        StatusAprovacaoCodigo statusAprovacaoCodigo,
        String hashArtefatosCodigo,
        OffsetDateTime codigoAprovadoEm,
        String codigoAprovadoPor,
        OffsetDateTime codigoInvalidadoEm,
        String motivoInvalidacaoCodigo,
        List<RegraAprovacaoCodigoDTO> regras
) {}
