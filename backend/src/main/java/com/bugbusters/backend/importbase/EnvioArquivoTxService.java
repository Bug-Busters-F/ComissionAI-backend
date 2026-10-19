package com.bugbusters.backend.importbase;

import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.model.InconsistenciaImportacao;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.repository.InconsistenciaImportacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class EnvioArquivoTxService {

    private final EnvioArquivoRepository repository;
    private final InconsistenciaImportacaoRepository inconsistenciaRepository;

    public EnvioArquivoTxService(EnvioArquivoRepository repository, InconsistenciaImportacaoRepository inconsistenciaRepository) {
        this.repository = repository;
        this.inconsistenciaRepository = inconsistenciaRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EnvioArquivo criarLotePendente(String idLote, String nomeArquivo, ImportType tipoBase, String competencia) {
        return criarLotePendente(idLote, nomeArquivo, tipoBase, competencia, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EnvioArquivo criarLotePendente(String idLote, String nomeArquivo, ImportType tipoBase, String competencia, String hashConteudo) {
        EnvioArquivo envio = new EnvioArquivo();
        envio.setIdLote(idLote);
        envio.setNomeArquivo(nomeArquivo != null ? nomeArquivo : "arquivo");
        envio.setTipoBase(tipoBase.name());
        envio.setCompetencia(competencia);
        envio.setHashConteudo(hashConteudo);
        envio.setStatus(ImportStatus.PENDENTE.name());
        envio.setTotalLinhas(0);
        envio.setLinhasValidas(0);
        envio.setRejeicaoIntegral(false);
        envio.setCriadoEm(OffsetDateTime.now());
        return repository.saveAndFlush(envio);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void vincularReportId(String idLote, Long reportId) {
        repository.findByIdLote(idLote).ifPresent(envio -> {
            envio.setReportId(reportId);
            repository.saveAndFlush(envio);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarStatusIniciado(Long reportId, OffsetDateTime iniciadoEm) {
        atualizarStatusIniciado(reportId, null, iniciadoEm);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarStatusIniciado(Long reportId, String idLote, OffsetDateTime iniciadoEm) {
        localizarLote(reportId, idLote).ifPresent(envio -> {
            if (reportId != null && envio.getReportId() == null) {
                envio.setReportId(reportId);
            }
            if (ImportStatus.PENDENTE.name().equals(envio.getStatus())) {
                envio.setStatus(ImportStatus.PROCESSANDO.name());
            }
            if (envio.getIniciadoEm() == null) {
                envio.setIniciadoEm(iniciadoEm != null ? iniciadoEm : OffsetDateTime.now());
            }
            repository.saveAndFlush(envio);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarStatusConcluido(Long reportId, long totalLinhas, OffsetDateTime finalizadoEm) {
        atualizarStatusConcluido(reportId, null, totalLinhas, finalizadoEm);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarStatusConcluido(Long reportId, String idLote, long totalLinhas, OffsetDateTime finalizadoEm) {
        atualizarStatusConcluido(reportId, idLote, totalLinhas, totalLinhas, null, finalizadoEm);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarStatusConcluido(Long reportId, String idLote, long totalLinhas, long linhasValidas, String mensagem, OffsetDateTime finalizadoEm) {
        localizarLote(reportId, idLote).ifPresent(envio -> {
            if (reportId != null && envio.getReportId() == null) {
                envio.setReportId(reportId);
            }
            envio.setStatus(ImportStatus.CONCLUIDO.name());
            envio.setTotalLinhas((int) totalLinhas);
            envio.setLinhasValidas((int) linhasValidas);
            envio.setRejeicaoIntegral(false);
            if (mensagem != null && !mensagem.isBlank()) {
                envio.setMensagem(mensagem);
            }
            OffsetDateTime fim = finalizadoEm != null ? finalizadoEm : OffsetDateTime.now();
            if (envio.getIniciadoEm() == null) {
                envio.setIniciadoEm(fim);
            }
            envio.setFinalizadoEm(fim);
            repository.saveAndFlush(envio);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvarInconsistenciasPorIdLote(String idLote, List<InconsistenciaImportacao> inconsistencias) {
        if (inconsistencias == null || inconsistencias.isEmpty()) {
            return;
        }
        repository.findByIdLote(idLote).ifPresent(envio -> {
            for (InconsistenciaImportacao inc : inconsistencias) {
                inc.setEnvio(envio);
            }
            inconsistenciaRepository.saveAll(inconsistencias);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarStatusFalhou(Long reportId, String mensagem, OffsetDateTime finalizadoEm) {
        atualizarStatusFalhou(reportId, null, mensagem, finalizadoEm);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarStatusFalhou(Long reportId, String idLote, String mensagem, OffsetDateTime finalizadoEm) {
        localizarLote(reportId, idLote).ifPresent(envio -> {
            if (reportId != null && envio.getReportId() == null) {
                envio.setReportId(reportId);
            }
            envio.setStatus(ImportStatus.FALHOU.name());
            envio.setTotalLinhas(0);
            envio.setLinhasValidas(0);
            envio.setRejeicaoIntegral(true);
            envio.setMensagem(mensagem);
            OffsetDateTime fim = finalizadoEm != null ? finalizadoEm : OffsetDateTime.now();
            if (envio.getIniciadoEm() == null) {
                envio.setIniciadoEm(fim);
            }
            envio.setFinalizadoEm(fim);
            repository.saveAndFlush(envio);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void atualizarStatusFalhouPorIdLote(String idLote, String mensagem, OffsetDateTime finalizadoEm) {
        atualizarStatusFalhou(null, idLote, mensagem, finalizadoEm);
    }

    private java.util.Optional<EnvioArquivo> localizarLote(Long reportId, String idLote) {
        if (reportId != null) {
            var porReport = repository.findByReportId(reportId);
            if (porReport.isPresent()) {
                return porReport;
            }
        }
        if (idLote != null && !idLote.isBlank()) {
            return repository.findByIdLote(idLote);
        }
        return java.util.Optional.empty();
    }
}
