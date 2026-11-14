package com.bugbusters.backend.dto.preparacao.impacto;

import java.util.List;
import java.util.UUID;

public record CalculoAfetadoDTO(
        String competencia,
        int qtdResultadosEmitidosAfetados,
        List<UUID> protocolosResultadosAfetados,
        List<UUID> idsVendasAfetadas,
        List<UUID> execucoesConcluidasSuperadas,
        String acaoAoConfirmar,
        String motivoImpacto
) {}
