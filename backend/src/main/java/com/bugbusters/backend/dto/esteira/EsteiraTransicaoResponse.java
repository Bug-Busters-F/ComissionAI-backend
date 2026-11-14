package com.bugbusters.backend.dto.esteira;

import com.bugbusters.backend.model.CampanhaEsteiraTransicao;
import com.bugbusters.backend.model.EstadoEsteiraCampanha;
import com.bugbusters.backend.model.TipoAcaoEsteira;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Registro de movimentação auditável na esteira da campanha")
public record EsteiraTransicaoResponse(
        @Schema(description = "ID do registro")
        Long id,

        @Schema(description = "ID da campanha")
        Long campanhaId,

        @Schema(description = "Número da versão da campanha")
        Integer versaoNumero,

        @Schema(description = "Etapa de origem")
        Integer etapaOrigem,

        @Schema(description = "Etapa de destino")
        Integer etapaDestino,

        @Schema(description = "Estado de origem")
        EstadoEsteiraCampanha estadoOrigem,

        @Schema(description = "Estado de destino")
        EstadoEsteiraCampanha estadoDestino,

        @Schema(description = "Tipo de ação realizada")
        TipoAcaoEsteira tipoAcao,

        @Schema(description = "Responsável pela ação")
        String responsavel,

        @Schema(description = "Justificativa, ressalva ou parecer da transição")
        String motivo,

        @Schema(description = "Data e hora do registro")
        OffsetDateTime criadoEm
) {
    public static EsteiraTransicaoResponse de(CampanhaEsteiraTransicao t) {
        if (t == null) {
            return null;
        }
        Long cId = t.getCampanhaVersao() != null && t.getCampanhaVersao().getCampanha() != null
                ? t.getCampanhaVersao().getCampanha().getId()
                : null;
        Integer num = t.getCampanhaVersao() != null ? t.getCampanhaVersao().getNumero() : null;

        return new EsteiraTransicaoResponse(
                t.getId(),
                cId,
                num,
                t.getEtapaOrigem(),
                t.getEtapaDestino(),
                t.getEstadoOrigem(),
                t.getEstadoDestino(),
                t.getTipoAcao(),
                t.getResponsavel(),
                t.getMotivo(),
                t.getCriadoEm()
        );
    }
}

