package com.bugbusters.backend.dto.preparacao.impacto;

import java.util.List;
import java.util.UUID;

public record PreviaImpactoCorrecaoDTO(
        UUID preparacaoId,
        Long campanhaOrigemId,
        String tipoBase,
        String competenciaLote,
        boolean possuiImpactos,
        boolean exigeConfirmacaoExplicita,
        boolean possuiBloqueioFechamento,
        List<String> bloqueiosFechamento,
        ResumoAlteracoesBaseDTO resumoAlteracoesBase,
        List<CampanhaAfetadaDTO> campanhasAfetadas,
        List<SimulacaoAfetadaDTO> simulacoesAfetadas,
        List<CalculoAfetadoDTO> calculosAfetados,
        List<AprovacaoCodigoAfetadaDTO> aprovacoesCodigoAfetadas,
        String mensagem
) {}
