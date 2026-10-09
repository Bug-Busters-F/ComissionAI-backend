package com.bugbusters.backend.dto.interpretador.proposta;

import java.util.List;

import com.bugbusters.backend.dto.regra.ConsultaConcorrenciaResponse;

public record ValidarPropostaResponse(
        boolean valida,
        List<String> erros,
        List<String> avisos,
        List<ReferenciaConsultadaDTO> referenciasValidadas,
        ConsultaConcorrenciaResponse analiseConcorrencia
) {}
