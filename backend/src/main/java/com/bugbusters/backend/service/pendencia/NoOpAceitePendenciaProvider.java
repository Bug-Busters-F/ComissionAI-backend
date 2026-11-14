package com.bugbusters.backend.service.pendencia;

import com.bugbusters.backend.model.InconsistenciaImportacao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Implementação padrão de contingência até que BACK-7 seja implementada.
 * Trata pendências como não-aceitas por padrão.
 */
@Component
@ConditionalOnMissingBean(value = AceitePendenciaProvider.class, ignored = NoOpAceitePendenciaProvider.class)
public class NoOpAceitePendenciaProvider implements AceitePendenciaProvider {

    @Override
    public StatusAceitePendenciaDTO consultarAceite(Long campanhaId, Long envioArquivoId, InconsistenciaImportacao inconsistencia) {
        return StatusAceitePendenciaDTO.naoAceita();
    }
}
