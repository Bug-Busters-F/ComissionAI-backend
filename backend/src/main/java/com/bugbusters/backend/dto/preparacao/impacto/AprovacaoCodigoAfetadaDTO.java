package com.bugbusters.backend.dto.preparacao.impacto;

import com.bugbusters.backend.model.StatusAprovacaoCodigo;

public record AprovacaoCodigoAfetadaDTO(
        Long campanhaId,
        Integer versaoNumero,
        Long regraId,
        String nomeRegra,
        StatusAprovacaoCodigo statusAtual,
        String acaoAoConfirmar,
        String motivoImpacto
) {}
