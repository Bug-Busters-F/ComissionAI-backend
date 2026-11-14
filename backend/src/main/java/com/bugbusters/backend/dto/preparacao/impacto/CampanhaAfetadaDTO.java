package com.bugbusters.backend.dto.preparacao.impacto;

import com.bugbusters.backend.model.EstadoCampanha;
import com.bugbusters.backend.model.StatusVersaoCampanha;
import java.util.List;

public record CampanhaAfetadaDTO(
        Long campanhaId,
        String titulo,
        EstadoCampanha estado,
        Integer versaoNumero,
        StatusVersaoCampanha statusVersao,
        boolean simulacaoAceitaInvalidada,
        boolean aprovacaoCodigoInvalidada,
        List<RegraAfetadaDTO> regrasAtingidas
) {}
