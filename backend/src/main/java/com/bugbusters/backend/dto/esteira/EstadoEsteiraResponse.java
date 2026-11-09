package com.bugbusters.backend.dto.esteira;

import com.bugbusters.backend.model.CampanhaVersao;
import com.bugbusters.backend.model.EstadoEsteiraCampanha;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Situação consolidada da campanha na esteira de aprovação")
public record EstadoEsteiraResponse(
        @Schema(description = "ID da campanha")
        Long campanhaId,

        @Schema(description = "Número da versão da campanha")
        Integer versaoNumero,

        @Schema(description = "Etapa corrente (1: Proposta, 2: Interpretação/Técnica, 3: Simulação, 4: Revisão Gerencial)")
        Integer etapaAtual,

        @Schema(description = "Etapa mais avançada já alcançada")
        Integer etapaMaisAvancada,

        @Schema(description = "Estado de governança da esteira")
        EstadoEsteiraCampanha estadoEsteira,

        @Schema(description = "Indica se a campanha foi devolvida para ajustes")
        boolean devolvida,

        @Schema(description = "Usuário responsável pela devolução")
        String devolvidoPor,

        @Schema(description = "Data/hora da devolução")
        OffsetDateTime devolvidoEm,

        @Schema(description = "Justificativa/motivo formal da devolução")
        String motivoDevolucao,

        @Schema(description = "Etapa de destino para a qual foi devolvida")
        Integer etapaDevolucao,

        @Schema(description = "Gerente que aprovou formalmente a campanha")
        String gerenteAprovador,

        @Schema(description = "Data/hora da aprovação gerencial")
        OffsetDateTime gerenteAprovadoEm,

        @Schema(description = "Parecer ou considerações do gerente")
        String parecerGerente,

        @Schema(description = "Indica se a revisão técnica do código Python está aprovada")
        boolean programaPythonAprovado,

        @Schema(description = "Indica se a simulação foi formalmente aceita")
        boolean simulacaoAceita
) {
    public static EstadoEsteiraResponse de(CampanhaVersao versao) {
        if (versao == null) {
            return null;
        }
        Long cId = versao.getCampanha() != null ? versao.getCampanha().getId() : null;
        boolean pythonOk = versao.getProgramaPythonAprovado() != null && versao.getProgramaPythonAprovado().isAprovado();
        boolean simulacaoOk = versao.getSimulacaoAceita() != null;

        return new EstadoEsteiraResponse(
                cId,
                versao.getNumero(),
                versao.getUltimaEtapa(),
                versao.getEtapaMaisAvancada(),
                versao.getEstadoEsteira(),
                versao.isDevolvida(),
                versao.getDevolvidoPor(),
                versao.getDevolvidoEm(),
                versao.getMotivoDevolucao(),
                versao.getEtapaDevolucao(),
                versao.getGerenteAprovador(),
                versao.getGerenteAprovadoEm(),
                versao.getParecerGerente(),
                pythonOk,
                simulacaoOk
        );
    }
}

