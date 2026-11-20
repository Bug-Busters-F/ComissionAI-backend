package com.bugbusters.backend.dto.auditoria;

import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FiltroAuditoriaDTO(
        String usuario,
        String perfilUsuario,
        TipoAcaoAuditoria tipoAcao,
        TipoEntidadeAuditoria tipoEntidade,
        String entidadeId,
        Long campanhaId,
        Long reportId,
        String idLote,
        Long envioArquivoId,
        UUID arquivoImportacaoId,
        UUID protocoloCalculo,
        UUID execucaoCalculoId,
        UUID fechamentoId,
        String competencia,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataInicio,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataFim,
        Boolean sucesso,
        String busca
) {
    /**
     * Construtor de compatibilidade para chamadas com os 7 filtros originais.
     */
    public FiltroAuditoriaDTO(
            String usuario,
            TipoAcaoAuditoria tipoAcao,
            TipoEntidadeAuditoria tipoEntidade,
            String entidadeId,
            OffsetDateTime dataInicio,
            OffsetDateTime dataFim,
            Boolean sucesso
    ) {
        this(usuario, null, tipoAcao, tipoEntidade, entidadeId, null, null, null, null, null,
                null, null, null, null, dataInicio, dataFim, sucesso, null);
    }

    public FiltroAuditoriaDTO comUsuario(String usuarioAlvo) {
        return new FiltroAuditoriaDTO(
                usuarioAlvo, perfilUsuario, tipoAcao, tipoEntidade, entidadeId, campanhaId, reportId, idLote,
                envioArquivoId, arquivoImportacaoId, protocoloCalculo, execucaoCalculoId, fechamentoId, competencia,
                dataInicio, dataFim, sucesso, busca
        );
    }
}

