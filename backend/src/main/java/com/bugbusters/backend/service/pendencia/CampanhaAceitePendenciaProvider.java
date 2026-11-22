package com.bugbusters.backend.service.pendencia;

import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.CampanhaPendenciaAceite;
import com.bugbusters.backend.model.CampanhaVersao;
import com.bugbusters.backend.model.InconsistenciaImportacao;
import com.bugbusters.backend.model.StatusAceitePendencia;
import com.bugbusters.backend.repository.CampanhaPendenciaAceiteRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Implementação concreta de {@link AceitePendenciaProvider} conectada aos aceites
 * rastreáveis persistidos em {@code tb_campanha_pendencia_aceite} (BACK-7 / BACK-14).
 */
@Component
public class CampanhaAceitePendenciaProvider implements AceitePendenciaProvider {

    private final CampanhaRepository campanhaRepository;
    private final CampanhaPendenciaAceiteRepository aceiteRepository;

    public CampanhaAceitePendenciaProvider(
            CampanhaRepository campanhaRepository,
            CampanhaPendenciaAceiteRepository aceiteRepository
    ) {
        this.campanhaRepository = campanhaRepository;
        this.aceiteRepository = aceiteRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public StatusAceitePendenciaDTO consultarAceite(
            Long campanhaId,
            Long envioArquivoId,
            InconsistenciaImportacao inconsistencia
    ) {
        if (campanhaId == null || inconsistencia == null) {
            return StatusAceitePendenciaDTO.naoAceita();
        }

        List<CampanhaPendenciaAceite> aceitesAtivos = carregarAceitesAtivosDaVersaoOuCampanha(campanhaId);
        if (aceitesAtivos.isEmpty()) {
            return StatusAceitePendenciaDTO.naoAceita();
        }

        Set<String> identificadoresEsperados = construirIdentificadoresCandidatos(envioArquivoId, inconsistencia);

        for (CampanhaPendenciaAceite aceite : aceitesAtivos) {
            if (correspondeAInconsistencia(aceite, envioArquivoId, inconsistencia, identificadoresEsperados)) {
                return StatusAceitePendenciaDTO.aceita(
                        aceite.getAceitoPor(),
                        aceite.getAceitoEm(),
                        aceite.getMotivoAceite()
                );
            }
        }
        return StatusAceitePendenciaDTO.naoAceita();
    }

    private List<CampanhaPendenciaAceite> carregarAceitesAtivosDaVersaoOuCampanha(Long campanhaId) {
        Campanha campanha = campanhaRepository.findByIdAndRemovidoEmIsNull(campanhaId).orElse(null);
        CampanhaVersao versaoAlvo = campanha == null ? null : campanha.getVersaoVigente() != null
                ? campanha.getVersaoVigente() : campanha.versaoParaRevisao();
        if (versaoAlvo != null) {
            List<CampanhaPendenciaAceite> daVersao = aceiteRepository.findAllByCampanhaVersaoIdAndStatus(
                    versaoAlvo.getId(), StatusAceitePendencia.ACEITA);
            // Nunca empresta aceite de uma proposta ou de uma versão histórica à vigente.
            return daVersao;
        }
        return aceiteRepository.findAllByCampanhaIdAndStatus(campanhaId, StatusAceitePendencia.ACEITA);
    }

    private boolean correspondeAInconsistencia(
            CampanhaPendenciaAceite aceite,
            Long envioArquivoId,
            InconsistenciaImportacao inconsistencia,
            Set<String> identificadoresEsperados
    ) {
        if (aceite.getInconsistenciaImportacaoId() != null && inconsistencia.getId() != null) {
            if (Objects.equals(aceite.getInconsistenciaImportacaoId(), inconsistencia.getId())) {
                return true;
            }
        }

        if (aceite.getPendenciaIdentificador() != null && !aceite.getPendenciaIdentificador().isBlank()) {
            String ident = aceite.getPendenciaIdentificador().trim().toLowerCase(Locale.ROOT);
            if (identificadoresEsperados.contains(ident)) {
                return true;
            }
        }

        if (aceite.getEnvioArquivoId() != null
                && envioArquivoId != null
                && Objects.equals(aceite.getEnvioArquivoId(), envioArquivoId)
                && aceite.getCampo() != null
                && inconsistencia.getCampo() != null
                && aceite.getCampo().equalsIgnoreCase(inconsistencia.getCampo())
                && aceite.getInconsistenciaImportacaoId() == null) {
            return true;
        }

        return false;
    }

    private Set<String> construirIdentificadoresCandidatos(Long envioArquivoId, InconsistenciaImportacao inc) {
        Set<String> candidatos = new LinkedHashSet<>();
        Long incId = inc.getId();
        String reportId = (inc.getEnvio() != null && inc.getEnvio().getReportId() != null)
                ? String.valueOf(inc.getEnvio().getReportId())
                : null;
        String campo = inc.getCampo() != null ? inc.getCampo().trim().toLowerCase(Locale.ROOT) : null;
        int linha = inc.getLinha();

        if (incId != null) {
            candidatos.add("inconsistencia:" + incId);
            candidatos.add("inc:" + incId);
            if (envioArquivoId != null) {
                candidatos.add("lote:" + envioArquivoId + ":inconsistencia:" + incId);
                candidatos.add("lote:" + envioArquivoId + ":inc:" + incId);
            }
            if (reportId != null && !reportId.isBlank()) {
                String rep = reportId.trim().toLowerCase(Locale.ROOT);
                candidatos.add("lote:" + rep + ":inconsistencia:" + incId);
                candidatos.add("lote:" + rep + ".inc:" + incId);
            }
        }

        if (reportId != null && !reportId.isBlank()) {
            String rep = reportId.trim().toLowerCase(Locale.ROOT);
            candidatos.add("lote:" + rep + ".linha:" + linha);
            candidatos.add("lote:" + rep + ":linha:" + linha);
            if (campo != null && !campo.isBlank()) {
                candidatos.add("lote:" + rep + ".linha:" + linha + ":" + campo);
                candidatos.add("lote:" + rep + ":linha:" + linha + ":" + campo);
            }
        }

        if (envioArquivoId != null) {
            candidatos.add("lote:" + envioArquivoId + ".linha:" + linha);
            candidatos.add("lote:" + envioArquivoId + ":linha:" + linha);
            if (campo != null && !campo.isBlank()) {
                candidatos.add("lote:" + envioArquivoId + ".linha:" + linha + ":" + campo);
                candidatos.add("lote:" + envioArquivoId + ":linha:" + linha + ":" + campo);
            }
        }

        return candidatos;
    }
}
