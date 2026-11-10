package com.bugbusters.backend.dto.preparacao.impacto;

import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.StatusAprovacaoCodigo;

public record RegraAfetadaDTO(
        Long regraId,
        String nomeRegra,
        StatusRegra statusRegra,
        StatusAprovacaoCodigo statusAprovacaoCodigoAtual,
        String motivoImpacto
) {}
