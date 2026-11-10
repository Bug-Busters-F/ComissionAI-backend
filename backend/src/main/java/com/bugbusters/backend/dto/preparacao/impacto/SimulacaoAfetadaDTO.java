package com.bugbusters.backend.dto.preparacao.impacto;

import com.bugbusters.backend.model.StatusAceiteSimulacao;
import java.util.UUID;

public record SimulacaoAfetadaDTO(
        UUID simulacaoId,
        UUID protocolo,
        Long campanhaId,
        String tituloCampanha,
        String competencia,
        StatusAceiteSimulacao statusAceiteAtual,
        int qtdVendasAtingidas,
        String acaoAoConfirmar,
        String motivoImpacto
) {}
