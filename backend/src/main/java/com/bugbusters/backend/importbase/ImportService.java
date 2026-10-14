package com.bugbusters.backend.importbase;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.importbase.batch.ImportJobListener;

@Service
public class ImportService {
    private final JobOperator jobOperator;
    private final Job job;
    private final JobRepository repository;
    private final ImportJobListener listener;

    public ImportService(@Qualifier("importJobOperator") JobOperator jobOperator,
            @Qualifier("importJob") Job job, JobRepository repository, ImportJobListener listener) {
        this.jobOperator = jobOperator;
        this.job = job;
        this.repository = repository;
        this.listener = listener;
    }

    public ImportResponse processImport(MultipartFile file, ImportType importType) {
        if (file == null || file.isEmpty() || importType == null) {
            throw new BusinessException("Informe um arquivo não vazio e o tipo de importação.");
        }
        Path upload = null;
        boolean submitted = false;
        try {
            upload = Files.createTempFile("commission-import-", ".upload");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, upload, StandardCopyOption.REPLACE_EXISTING);
            }
            String filename = file.getOriginalFilename() == null ? "arquivo" : file.getOriginalFilename();
            var parameters = new JobParametersBuilder()
                    .addString("requestId", UUID.randomUUID().toString())
                    .addString("importType", importType.name())
                    .addString("filename", filename, false)
                    .addString("filePath", upload.toString(), false)
                    .toJobParameters();
            var execution = jobOperator.start(job, parameters);
            submitted = true;
            // Executor rejection does not invoke afterJob; its execution is marked FAILED.
            if (execution.getStatus() == BatchStatus.FAILED) {
                listener.deleteUpload(upload);
            }
            // Query persisted metadata instead of sharing the worker's mutable JobExecution.
            return getReport(execution.getId());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível iniciar a importação.", ex);
        } finally {
            if (!submitted && upload != null) {
                listener.deleteUpload(upload);
            }
        }
    }

    public ImportResponse getReport(long reportId) {
        JobExecution execution;
        try {
            execution = repository.getJobExecution(reportId);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Relatório de importação não encontrado: " + reportId);
        }
        if (execution == null || !job.getName().equals(execution.getJobInstance().getJobName())) {
            throw new ResourceNotFoundException("Relatório de importação não encontrado: " + reportId);
        }
        ImportStatus status = switch (execution.getStatus()) {
            case STARTING -> ImportStatus.PENDENTE;
            case STARTED, STOPPING -> ImportStatus.PROCESSANDO;
            case COMPLETED -> ImportStatus.CONCLUIDO;
            case FAILED, STOPPED, ABANDONED, UNKNOWN -> ImportStatus.FALHOU;
        };
        Long count = null;
        if (status == ImportStatus.CONCLUIDO) {
            count = execution.getStepExecutions().stream().mapToLong(step -> step.getWriteCount()).sum();
        } else if (status == ImportStatus.FALHOU) {
            count = 0L;
        }
        String message = status == ImportStatus.FALHOU
                ? execution.getExecutionContext().getString("failureMessage", "Não foi possível concluir a importação.")
                : null;
        return new ImportResponse(reportId, execution.getJobParameters().getString("filename"),
                ImportType.valueOf(execution.getJobParameters().getString("importType")), status, count, message);
    }
}
