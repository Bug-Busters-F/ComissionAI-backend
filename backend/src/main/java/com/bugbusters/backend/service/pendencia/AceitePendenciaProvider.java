package com.bugbusters.backend.service.pendencia;

import com.bugbusters.backend.model.InconsistenciaImportacao;

/**
 * Ponto de extensão para consulta do aceite de pendências de importação (integração com BACK-7).
 */
public interface AceitePendenciaProvider {

    /**
     * Consulta se uma inconsistência/pendência possui aceite registrado para a campanha.
     *
     * @param campanhaId      ID da campanha
     * @param envioArquivoId  ID interno do lote (tb_envio_arquivo.id)
     * @param inconsistencia  entidade da inconsistência
     * @return status do aceite
     */
    StatusAceitePendenciaDTO consultarAceite(Long campanhaId, Long envioArquivoId, InconsistenciaImportacao inconsistencia);
}
