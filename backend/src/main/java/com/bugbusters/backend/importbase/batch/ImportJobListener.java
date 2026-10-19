package com.bugbusters.backend.importbase.batch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.importbase.EnvioArquivoTxService;

@Component
public class ImportJobListener implements JobExecutionListener {
    private static final Logger log = LoggerFactory.getLogger(ImportJobListener.class);
    private final JobRepository repository;
    private final EnvioArquivoTxService txService;

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
        try {
            String idLote = execution.getJobParameters().getString("requestId");
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                long writeCount = execution.getStepExecutions().stream().mapToLong(step -> step.getWriteCount()).sum();
                long readCount = execution.getStepExecutions().stream().mapToLong(step -> step.getReadCount()).sum();
                String warningMessage = execution.getExecutionContext().getString("warningMessage", null);
                long totalLinhas = readCount > 0 ? readCount : writeCount;
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
                try {
                    txService.atualizarStatusFalhou(execution.getId(), idLote, msg, OffsetDateTime.now());
                } catch (Exception ex) {
                    log.warn("Não foi possível atualizar o status do lote para FALHOU: {}", ex.getMessage(), ex);
                }
            }
        } finally {
            deleteUpload(Path.of(execution.getJobParameters().getString("filePath")));
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
