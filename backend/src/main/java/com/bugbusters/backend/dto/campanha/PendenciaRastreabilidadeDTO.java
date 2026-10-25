package com.bugbusters.backend.dto.campanha;

import com.bugbusters.backend.dto.interpretador.proposta.PendenciaPropostaDTO;
import com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia;
import java.util.List;

/** Preserva classificação e cobertura de validação ao reabrir um rascunho. */
public record PendenciaRastreabilidadeDTO(String id, String campo, String codigo, String mensagem,
        SeveridadePendencia severidade, String origem, String escopo, Boolean exigeValidacaoRemota,
        List<String> camposRelacionados) {
    public PendenciaPropostaDTO paraProposta() {
        return new PendenciaPropostaDTO(campo, codigo, mensagem, severidade);
    }
}
