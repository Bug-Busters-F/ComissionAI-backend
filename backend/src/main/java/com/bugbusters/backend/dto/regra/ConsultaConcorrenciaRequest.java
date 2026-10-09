package com.bugbusters.backend.dto.regra;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConsultaConcorrenciaRequest(
        Long campanhaId,
        Integer codMarca,
        Integer codCargo,
        Integer codLoja,
        String canal,
        String matricula,
        BigDecimal taxa,
        LocalDate dataInicio,
        LocalDate dataFim
) {
    public ConsultaConcorrenciaRequest(
            Long campanhaId,
            Integer codMarca,
            Integer codCargo,
            Integer codLoja,
            String canal,
            String matricula,
            LocalDate dataInicio,
            LocalDate dataFim) {
        this(campanhaId, codMarca, codCargo, codLoja, canal, matricula, null, dataInicio, dataFim);
    }
}

