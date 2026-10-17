package com.bugbusters.backend.dto.interpretador.proposta;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ValidarPropostaRequest(
        Long campanhaId,
        Integer codMarca,
        String descrMarca,
        Integer codCargo,
        String descriCargo,
        Integer codLoja,
        String canal,
        String matricula,
        BigDecimal taxa,
        LocalDate dataInicio,
        LocalDate dataFim
) {}
