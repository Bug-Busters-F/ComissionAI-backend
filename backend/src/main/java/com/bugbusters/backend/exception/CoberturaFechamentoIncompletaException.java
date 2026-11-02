package com.bugbusters.backend.exception;

import com.bugbusters.backend.dto.cobertura.CoberturaCampanhaResponseDTO;
import com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO;

import java.util.ArrayList;
import java.util.List;

public class CoberturaFechamentoIncompletaException extends RuntimeException {

    private final CoberturaCampanhaResponseDTO diagnostico;
    private final List<CampoInvalidoDTO> validacoes;

    public CoberturaFechamentoIncompletaException(String message, CoberturaCampanhaResponseDTO diagnostico, List<CampoInvalidoDTO> validacoes) {
        super(message);
        this.diagnostico = diagnostico;
        this.validacoes = validacoes != null ? validacoes : List.of();
    }

    public CoberturaCampanhaResponseDTO getDiagnostico() {
        return diagnostico;
    }

    public List<CampoInvalidoDTO> getValidacoes() {
        return validacoes;
    }

    public static CoberturaFechamentoIncompletaException aPartirDe(CoberturaCampanhaResponseDTO diagnostico) {
        List<CampoInvalidoDTO> falhas = new ArrayList<>();
        if (diagnostico.resumoImpedimentos() != null) {
            for (String imp : diagnostico.resumoImpedimentos()) {
                falhas.add(new CampoInvalidoDTO("cobertura", imp));
            }
        }
        String msg = "Validação de cobertura para fechamento falhou: a campanha possui pendências ou faltas impeditivas.";
        return new CoberturaFechamentoIncompletaException(msg, diagnostico, falhas);
    }
}
