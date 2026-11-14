package com.bugbusters.backend.exception;

import com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO;
import java.util.Collections;
import java.util.List;

/**
 * Exceção lançada quando a população selecionada para o recorte da IA
 * apresenta dados incompletos, ausência de taxas base contratuais,
 * pendências não aceitas ou inconsistências impeditivas para cálculo reproduzível.
 */
public class RecortePopulacaoIncompletoException extends RuntimeException {

    private final List<CampoInvalidoDTO> validacoes;

    public RecortePopulacaoIncompletoException(String message) {
        super(message);
        this.validacoes = Collections.emptyList();
    }

    public RecortePopulacaoIncompletoException(String message, List<CampoInvalidoDTO> validacoes) {
        super(message);
        this.validacoes = validacoes != null ? Collections.unmodifiableList(validacoes) : Collections.emptyList();
    }

    public List<CampoInvalidoDTO> getValidacoes() {
        return validacoes;
    }
}
