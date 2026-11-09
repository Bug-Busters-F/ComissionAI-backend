package com.bugbusters.backend.dto.auditoria;

import com.bugbusters.backend.model.AuditoriaGeral;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Item detalhado da trilha de auditoria geral (BUG-111 / BACK-16)")
public record AuditoriaResponseDTO(
        @Schema(description = "ID do registro")
        UUID id,

        @Schema(description = "Protocolo único imutável de auditoria")
        UUID protocolo,

        @Schema(description = "Data e hora exata do registro")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime dataHora,

        @Schema(description = "Identificação do usuário ou agente")
        String usuario,

        @Schema(description = "Perfil ou papel do usuário")
        String perfilUsuario,

        @Schema(description = "Tipo da ação auditável")
        TipoAcaoAuditoria tipoAcao,

        @Schema(description = "Tipo do recurso/entidade")
        TipoEntidadeAuditoria tipoEntidade,

        @Schema(description = "Identificador do recurso")
        String entidadeId,

        @Schema(description = "Número da versão do recurso")
        Integer versaoEntidade,

        @Schema(description = "Descrição legível da ação")
        String descricao,

        @Schema(description = "Dados anteriores em JSON")
        String dadosAnteriores,

        @Schema(description = "Dados novos em JSON")
        String dadosNovos,

        @Schema(description = "IP de origem")
        String ipOrigem,

        @Schema(description = "User Agent")
        String userAgent,

        @Schema(description = "Indica sucesso ou falha da ação")
        boolean sucesso,

        @Schema(description = "Detalhe de erro em caso de falha")
        String detalheErro
) {
    public static AuditoriaResponseDTO de(AuditoriaGeral a) {
        return new AuditoriaResponseDTO(
                a.getId(),
                a.getProtocolo(),
                a.getDataHora(),
                a.getUsuario(),
                a.getPerfilUsuario(),
                a.getTipoAcao(),
                a.getTipoEntidade(),
                a.getEntidadeId(),
                a.getVersaoEntidade(),
                a.getDescricao(),
                a.getDadosAnteriores(),
                a.getDadosNovos(),
                a.getIpOrigem(),
                a.getUserAgent(),
                a.isSucesso(),
                a.getDetalheErro()
        );
    }
}

