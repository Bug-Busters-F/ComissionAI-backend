package com.bugbusters.backend.dto.execucao;

import com.bugbusters.backend.model.StatusEnquadramentoOrcamento;
import com.bugbusters.backend.model.StatusExecucaoCalculo;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExecucaoCalculoResponse(
        UUID id, String competencia, boolean recalcular, StatusExecucaoCalculo status,
        int totalItens, int itensProcessados, int percentual, String referenciaDados,
        String referenciaRegras, OffsetDateTime solicitadoEm, OffsetDateTime iniciadoEm,
        OffsetDateTime concluidoEm, String falha, String resultado,
        BigDecimal orcamentoTotalCampanhas,
        BigDecimal comissaoTotalApurada,
        StatusEnquadramentoOrcamento statusEnquadramento,
        String resumoOrcamentoJson
) {
    public ExecucaoCalculoResponse(UUID id, String competencia, boolean recalcular, StatusExecucaoCalculo status,
                                  int totalItens, int itensProcessados, int percentual, String referenciaDados,
                                  String referenciaRegras, OffsetDateTime solicitadoEm, OffsetDateTime iniciadoEm,
                                  OffsetDateTime concluidoEm, String falha, String resultado) {
        this(id, competencia, recalcular, status, totalItens, itensProcessados, percentual, referenciaDados,
                referenciaRegras, solicitadoEm, iniciadoEm, concluidoEm, falha, resultado, null, null, null, null);
    }
}
