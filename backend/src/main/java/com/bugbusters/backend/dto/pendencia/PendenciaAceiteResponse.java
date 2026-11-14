package com.bugbusters.backend.dto.pendencia;

import com.bugbusters.backend.dto.interpretador.proposta.SeveridadePendencia;
import com.bugbusters.backend.model.CampanhaPendenciaAceite;
import com.bugbusters.backend.model.StatusAceitePendencia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Resposta com os dados completos e rastreáveis do aceite de uma pendência.
 */
@Schema(description = "Registro rastreável do aceite de pendência")
public record PendenciaAceiteResponse(
        Long id,
        Long campanhaId,
        Long campanhaVersaoId,
        Long regraId,
        String pendenciaIdentificador,
        String codigo,
        String campo,
        String mensagem,
        SeveridadePendencia severidade,
        StatusAceitePendencia status,
        String aceitoPor,
        OffsetDateTime aceitoEm,
        String motivoAceite,
        String revogadoPor,
        OffsetDateTime revogadoEm,
        String motivoRevogacao
) {
    public static PendenciaAceiteResponse de(CampanhaPendenciaAceite entidade) {
        if (entidade == null) return null;
        return new PendenciaAceiteResponse(
                entidade.getId(),
                entidade.getCampanha() != null ? entidade.getCampanha().getId() : null,
                entidade.getCampanhaVersao() != null ? entidade.getCampanhaVersao().getId() : null,
                entidade.getRegra() != null ? entidade.getRegra().getId() : null,
                entidade.getPendenciaIdentificador(),
                entidade.getCodigo(),
                entidade.getCampo(),
                entidade.getMensagem(),
                entidade.getSeveridade(),
                entidade.getStatus(),
                entidade.getAceitoPor(),
                entidade.getAceitoEm(),
                entidade.getMotivoAceite(),
                entidade.getRevogadoPor(),
                entidade.getRevogadoEm(),
                entidade.getMotivoRevogacao()
        );
    }
}

