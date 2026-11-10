package com.bugbusters.backend.dto.preparacao.impacto;

import java.util.List;

public record ResumoAlteracoesBaseDTO(
        int registrosNovos,
        int registrosAtualizados,
        int registrosInalterados,
        List<String> competenciasAtingidas,
        List<String> matriculasAtingidas,
        List<Integer> lojasAtingidas,
        List<Integer> cargosAtingidos,
        List<Integer> marcasAtingidas
) {}
