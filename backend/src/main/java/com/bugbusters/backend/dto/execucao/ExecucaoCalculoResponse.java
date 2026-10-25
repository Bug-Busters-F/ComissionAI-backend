package com.bugbusters.backend.dto.execucao;

import com.bugbusters.backend.model.StatusExecucaoCalculo;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExecucaoCalculoResponse(UUID id, String competencia, boolean recalcular, StatusExecucaoCalculo status,
                                      int totalItens, int itensProcessados, int percentual, String referenciaDados,
                                      String referenciaRegras, OffsetDateTime solicitadoEm, OffsetDateTime iniciadoEm,
                                      OffsetDateTime concluidoEm, String falha, String resultado) {}
