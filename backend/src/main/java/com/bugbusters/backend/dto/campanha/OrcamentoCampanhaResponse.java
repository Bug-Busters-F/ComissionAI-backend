package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.model.OrigemOrcamentoCampanha;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Detalhes do orçamento monetário persistido e confirmado na campanha/proposta")
public record OrcamentoCampanhaResponse(
        Long campanhaId,
        Integer versaoNumero,
        BigDecimal orcamento,
        BigDecimal orcamentoExtraidoIa,
        OrigemOrcamentoCampanha origemOrcamento,
        boolean orcamentoConfirmado,
        String orcamentoConfirmadoPor,
        OffsetDateTime orcamentoConfirmadoEm,
        String justificativaOrcamento,
        boolean alteradoEmRelacaoAoExtraido,
        boolean simulacaoInvalidada
) {}
