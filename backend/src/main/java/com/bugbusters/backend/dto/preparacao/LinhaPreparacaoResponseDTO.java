package com.bugbusters.backend.dto.preparacao;

import com.bugbusters.backend.importbase.validation.ErroValidacaoLinhaDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LinhaPreparacaoResponseDTO(
        UUID id,
        int numeroLinha,
        Long versao,
        String statusLinha,
        boolean valida,
        String severidadeMaxima,
        boolean editada,
        String origemUltimaEdicao,
        boolean ignorarNaEfetivacao,
        UUID registroAlvoId,
        Map<String, Object> conteudoOriginal,
        DadosInterpretadosDTO dadosInterpretados,
        List<ErroValidacaoLinhaDTO> erros,
        OffsetDateTime atualizadoEm,
        String atualizadoPor
) {
    public record DadosInterpretadosDTO(
            LocalDate dataReferencia,
            Integer codMarca,
            String descrMarca,
            Integer codLoja,
            String descrLoja,
            String matricula,
            LocalDate dataAdmissao,
            LocalDate dataDemissao,
            Integer codCargo,
            String descrCargo,
            BigDecimal valorVenda
    ) {}
}
