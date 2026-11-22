package com.bugbusters.backend.importbase.batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.dto.auditoria.AuditoriaEventoContexto;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.importbase.EnvioArquivoTxService;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.service.AuditoriaGeralService;

@Component
public class ImportJobListener implements JobExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(ImportJobListener.class);
    private final JobRepository repository;
    private final EnvioArquivoTxService txService;

    @Autowired(required = false)
    private AuditoriaGeralService auditoriaService;

    @Autowired(required = false)
    private EnvioArquivoRepository envioArquivoRepository;

    public ImportJobListener(JobRepository repository, EnvioArquivoTxService txService) {
        this.repository = repository;
        this.txService = txService;
    }

    @Override
    public void beforeJob(JobExecution execution) {
        try {
            String idLote = execution.getJobParameters().getString("requestId");
            txService.atualizarStatusIniciado(execution.getId(), idLote, OffsetDateTime.now());
        } catch (Exception ex) {
            log.warn("Não foi possível atualizar o status do lote para PROCESSANDO: {}", ex.getMessage(), ex);
        }
    }

    @Override
    public void afterJob(JobExecution execution) {
        String filePath = execution.getJobParameters().getString("filePath");
        try {
            if (filePath != null && !filePath.isBlank()) {
                deleteUpload(Path.of(filePath));
            }
            String idLote = execution.getJobParameters().getString("requestId");
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                long writeCount = execution.getStepExecutions().stream().mapToLong(step -> step.getWriteCount()).sum();
                long readCount = execution.getStepExecutions().stream().mapToLong(step -> step.getReadCount()).sum();
                String warningMessage = execution.getExecutionContext().getString("warningMessage", null);
                long totalLinhas = readCount > 0 ? readCount : writeCount;
                registrarAuditoriaImportacao(execution, idLote, true, writeCount, totalLinhas, warningMessage);
                try {
                    txService.atualizarStatusConcluido(execution.getId(), idLote, totalLinhas, writeCount, warningMessage, OffsetDateTime.now());
                } catch (Exception ex) {
                    log.warn("Não foi possível atualizar o status do lote para CONCLUIDO: {}", ex.getMessage(), ex);
                }
            } else {
                String msg = failureMessage(execution);
                execution.getExecutionContext().putString("failureMessage", msg);
                // Throwable instances are not restored when querying JDBC metadata.
                repository.updateExecutionContext(execution);
                registrarAuditoriaImportacao(execution, idLote, false, 0L, 0L, msg);
                try {
                    txService.atualizarStatusFalhou(execution.getId(), idLote, msg, OffsetDateTime.now());
                } catch (Exception ex) {
                    log.warn("Não foi possível atualizar o status do lote para FALHOU: {}", ex.getMessage(), ex);
                }
            }
        } finally {
            if (filePath != null && !filePath.isBlank()) {
                deleteUpload(Path.of(filePath));
            }
        }
    }

    private void registrarAuditoriaImportacao(
            JobExecution execution,
            String idLote,
            boolean sucesso,
            long writeCount,
            long totalLinhas,
            String detalheMensagem
    ) {
        if (auditoriaService == null) {
            return;
        }
        try {
            String criadoPorParam = execution.getJobParameters().getString("criadoPor");
            String perfilParam = execution.getJobParameters().getString("perfilCriador");
            String arquivoIdParam = execution.getJobParameters().getString("arquivoImportacaoId");
            String filenameParam = execution.getJobParameters().getString("filename");
            String competenciaParam = execution.getJobParameters().getString("competencia");
            String tipoBaseParam = execution.getJobParameters().getString("importType");

            EnvioArquivo envio = envioArquivoRepository != null && idLote != null
                    ? envioArquivoRepository.findByIdLote(idLote).orElse(null)
                    : null;

            UUID arquivoId = envio != null && envio.getArquivoImportacaoId() != null
                    ? envio.getArquivoImportacaoId()
                    : parseUuid(arquivoIdParam);
            String nomeArquivo = envio != null ? envio.getNomeArquivo() : filenameParam;
            String tipoBase = envio != null ? envio.getTipoBase() : tipoBaseParam;
            String competencia = envio != null ? envio.getCompetencia() : competenciaParam;
            String usuario = envio != null ? envio.getCriadoPor() : criadoPorParam;
            String perfil = envio != null && envio.getPerfilCriador() != null ? envio.getPerfilCriador() : perfilParam;

            String desc = sucesso
                    ? String.format("Importação %s (%s) concluída: %d/%d linhas válidas.",
                    tipoBase, nomeArquivo, writeCount, totalLinhas)
                    : String.format("Importação %s (%s) falhou: %s",
                    tipoBase, nomeArquivo, detalheMensagem != null ? detalheMensagem : "erro na validação.");

            auditoriaService.registrarEventoIsolado(AuditoriaEventoContexto.builder()
                    .usuario(usuario)
                    .perfilUsuario(perfil)
                    .tipoAcao(TipoAcaoAuditoria.IMPORTACAO_BASE)
                    .tipoEntidade(TipoEntidadeAuditoria.LOTE_IMPORTACAO)
                    .entidadeId(String.valueOf(execution.getId()))
                    .envioArquivoId(envio != null ? envio.getId() : null)
                    .arquivoImportacaoId(arquivoId)
                    .reportId(execution.getId())
                    .idLote(idLote)
                    .nomeArquivo(nomeArquivo)
                    .competencia(competencia)
                    .descricao(desc)
                    .dadosNovos(String.format("{\"reportId\":%d,\"tipoBase\":\"%s\",\"status\":\"%s\",\"linhasValidas\":%d,\"totalLinhas\":%d}",
                            execution.getId(), tipoBase, sucesso ? "CONCLUIDO" : "FALHOU", writeCount, totalLinhas))
                    .sucesso(sucesso)
                    .detalheErro(sucesso ? null : detalheMensagem));
        } catch (Exception ex) {
            log.warn("Falha ao registrar trilha de auditoria do lote {}: {}", execution.getId(), ex.getMessage());
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String failureMessage(JobExecution execution) {
        for (Throwable failure : execution.getAllFailureExceptions()) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof BusinessException) {
                    return cause.getMessage();
                }
            }
            log.error("Erro inesperado durante a execução do job {}: {}", execution.getId(), failure.getMessage(), failure);
        }
        return "Não foi possível concluir a importação. Nenhum registro foi confirmado.";
    }

    public void deleteUpload(Path upload) {
        try {
            Files.deleteIfExists(upload);
        } catch (IOException ex) {
            log.warn("Não foi possível remover o arquivo temporário de importação: {}", upload, ex);
        }
    }
}
