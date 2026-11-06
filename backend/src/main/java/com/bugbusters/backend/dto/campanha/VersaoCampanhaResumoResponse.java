package com.bugbusters.backend.dto.campanha;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VersaoCampanhaResumoResponse(
        Integer numero,
        Integer versaoAnterior,
        String responsavel,
        OffsetDateTime publicadoEm,
        UUID simulacaoAceitaId,
        UUID protocoloSimulacao,
        BigDecimal orcamento,
        Boolean orcamentoConfirmado
) {
    public VersaoCampanhaResumoResponse(Integer numero, Integer versaoAnterior, String responsavel,
                                        OffsetDateTime publicadoEm, UUID simulacaoAceitaId, UUID protocoloSimulacao) {
        this(numero, versaoAnterior, responsavel, publicadoEm, simulacaoAceitaId, protocoloSimulacao, null, false);
    }
}
