package com.bugbusters.backend.dto.campanha;

import java.util.List;

public record ComparacaoVersaoResponse(
        Long campanhaId, Integer versaoBase, Integer versaoComparada,
        List<AlteracaoCampoDTO> alteracoesCampanha, List<AlteracaoRegraDTO> alteracoesRegras
) {
    public record AlteracaoCampoDTO(String campo, Object anterior, Object atual) {}
    public record AlteracaoRegraDTO(String identificadorLogico, TipoAlteracao tipo,
                                    List<AlteracaoCampoDTO> alteracoes) {}
    public enum TipoAlteracao { ADICIONADA, REMOVIDA, ALTERADA, SEM_ALTERACAO }
}
