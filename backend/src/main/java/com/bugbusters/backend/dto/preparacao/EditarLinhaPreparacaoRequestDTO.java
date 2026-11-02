package com.bugbusters.backend.dto.preparacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EditarLinhaPreparacaoRequestDTO(
        Long versao,
        String origem,
        String responsavel,
        String motivo,
        Boolean ignorarNaEfetivacao,
        UUID registroAlvoId,
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
) {
    public EditarLinhaPreparacaoRequestDTO(Long versao, String motivo, Integer codCargo, String descrCargo) {
        this(versao, "MANUAL", null, motivo, null, null, null, null, null, null, null, null, null, null, codCargo, descrCargo, null);
    }
}
