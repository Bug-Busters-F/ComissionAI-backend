package com.bugbusters.backend.dto.regra;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegraConcorrenteDTO(
        Long regraId,
        Long campanhaId,
        String nomeCampanha,
        String nomeRegra,
        String canal,
        Integer codMarca,
        String descrMarca,
        Integer codCargo,
        String descriCargo,
        Integer codLoja,
        String matricula,
        BigDecimal taxa,
        LocalDate dataInicio,
        LocalDate dataFim,
        int especificidadeProposta,
        int especificidadeConcorrente,
        TipoConcorrenciaRegra tipoConcorrencia,
        String mensagemExplicativa
) {}
