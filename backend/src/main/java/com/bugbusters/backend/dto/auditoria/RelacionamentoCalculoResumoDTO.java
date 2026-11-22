package com.bugbusters.backend.dto.auditoria;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resumo relacional de cálculo, simulação, apuração, execução ou fechamento vinculado ao evento.
 */
@Schema(description = "Relacionamento do evento com cálculo, execução, apuração, fechamento ou simulação")
public record RelacionamentoCalculoResumoDTO(
        UUID protocoloCalculo,
        UUID logCalculoId,
        UUID execucaoCalculoId,
        UUID apuracaoCampanhaId,
        UUID fechamentoId,
        UUID simulacaoId,
        String competencia,
        String status,
        String origemExecucao,
        Integer totalItens,
        Integer totalImpedimentos,
        BigDecimal valorTotalVendas,
        BigDecimal valorTotalComissao
) {}
