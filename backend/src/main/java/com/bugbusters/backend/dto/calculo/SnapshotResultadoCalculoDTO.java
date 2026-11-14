package com.bugbusters.backend.dto.calculo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SnapshotResultadoCalculoDTO(
        UUID id,
        Long resultadoCalculoId,
        UUID protocoloCalculo,
        UUID idVenda,
        String matricula,
        Integer codMarca,
        Integer codLoja,
        Integer codCargo,
        Long regraId,
        String origemTaxa,
        LocalDate dataVenda,
        BigDecimal valorVenda,
        BigDecimal taxaAplicada,
        BigDecimal valorComissao,
        BigDecimal valorBonusPercentual,
        BigDecimal valorBonusFixo,
        Integer versaoCampanhaNumero,
        UUID fechamentoId,
        String tipoCalculo,
        OffsetDateTime calculadoEm,
        OffsetDateTime preservadoEm,
        String motivoPreservacao,
        UUID lotePreparacaoId,
        UUID substituidoPorProtocolo
) {}
